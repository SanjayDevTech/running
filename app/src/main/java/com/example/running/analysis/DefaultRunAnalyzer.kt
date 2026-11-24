package com.example.running.analysis

import com.example.running.core.contracts.RunAnalyzer
import com.example.running.core.contracts.RunNamer
import com.example.running.core.model.ActivityType
import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.ElevationSource
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.PauseInterval
import com.example.running.core.model.PointKind
import com.example.running.core.model.RunAnalysis
import com.example.running.core.model.RunStats
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.SensorType
import com.example.running.core.model.TrackPoint
import com.example.running.core.util.paceSecPerKm
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Full post-run analysis (pure Kotlin). Pipeline:
 * 1. GPS pre-filter (accuracy > 30 m, speed >= 15 m/s, < 0.5 m jitter) on non-paused fixes.
 * 2. Kalman filter + RTS smoother (position + Doppler velocity) per continuous stretch.
 * 3. Stationary gating from step cadence, Doppler and windowed displacement; stops debounced and
 *    positions frozen while stopped (Strava-style moving time).
 * 4. Interval distance: Doppler/position blend on good GPS; step distance (learned/local stride) when
 *    accuracy is worse than ~20 m or fixes are missing, flagged `stepFilled` (drawn dashed).
 * 5. Walk/run labelling from cadence + speed, merged into segments; type from the walk share.
 * 6. Elevation (barometer, else smoothed GPS), splits, best efforts on moving time, calories.
 */
internal class DefaultRunAnalyzer(private val namer: RunNamer) : RunAnalyzer {

    private class Base(val raw: TrackPoint, val excludedBefore: Boolean) {
        val t = raw.timeMillis
        val acc = raw.horizontalAccuracyM?.toDouble() ?: AnalysisTuning.DEFAULT_ACCURACY_M
        val doppler = raw.speedMps?.toDouble()
        val dopplerOk = doppler != null && (raw.speedAccuracyMps?.toDouble() ?: 0.5) <= AnalysisTuning.DOPPLER_MAX_SPEED_ACCURACY
        var east = 0.0
        var north = 0.0
        var kalmanSpeed = 0.0
        var windowSpeed = 0.0
        var stationary = false
    }

    private class Interval {
        var excluded = false
        var stopped = false
        var gap = false
        var weak = false
        var fill = false
        var dist = 0.0
        var steps = 0.0
        var gait = Gait.RUN
    }

    override fun analyze(input: AnalysisInput): RunAnalysis {
        val start = input.startedAtMillis
        val end = max(input.endedAtMillis, start)
        val pauses = input.pauses.map { PauseInterval(it.startMillis, it.endMillis ?: end) }
        val stepTimeline = StepTimeline.from(input.sensorSamples, pauses)
        val hasSteps = stepTimeline.hasData
        val profile = input.profile

        val base = acceptFixes(input.rawPoints, pauses)
        val n = base.size
        val projection = base.firstOrNull()?.let { LocalProjection(GeoPoint(it.raw.lat, it.raw.lon)) }
        if (projection != null) smoothPositions(base, projection)
        computeWindowSpeeds(base)

        // ---- per-point stationary flags ----
        for (p in base) p.stationary = isStationary(p, hasSteps, stepTimeline)

        // ---- intervals ----
        val iv = Array(n) { Interval() }
        if (n > 0) iv[0].excluded = true
        for (i in 1 until n) {
            val a = base[i - 1]
            val b = base[i]
            val it = iv[i]
            it.excluded = b.excludedBefore
            if (it.excluded) continue
            val dt = b.t - a.t
            it.gap = dt > AnalysisTuning.GAP_MILLIS
            it.weak = max(a.acc, b.acc) > AnalysisTuning.WEAK_ACCURACY_M
            it.steps = if (hasSteps) stepTimeline.stepsBetween(a.t, b.t) else 0.0
            it.stopped = if (it.gap) {
                if (hasSteps) stepTimeline.cadence(a.t, b.t) < AnalysisTuning.STILL_CADENCE_SPM
                else straight(a, b) / (dt / 1000.0) < AnalysisTuning.STOP_SPEED_MPS
            } else {
                a.stationary && b.stationary
            }
        }
        debounceStops(base, iv)
        freezeStops(base, iv)

        // ---- GPS distance on good intervals, preliminary gait, stride measurement ----
        val canFill = hasSteps
        for (i in 1 until n) {
            val it = iv[i]
            if (it.excluded || it.stopped) continue
            it.fill = canFill && (it.gap || it.weak)
            if (!it.fill) it.dist = gpsDistance(base[i - 1], base[i], it.gap)
            val dt = base[i].t - base[i - 1].t
            val cadence = if (hasSteps) stepTimeline.cadenceAround((base[i - 1].t + base[i].t) / 2, AnalysisTuning.GAIT_HALF_WINDOW_MILLIS) else null
            val speed = if (!it.fill) it.dist / (dt.coerceAtLeast(1) / 1000.0) else 0.0
            it.gait = GaitClassifier.classify(cadence, if (it.fill) (if ((cadence ?: 0.0) >= AnalysisTuning.RUN_CADENCE_SPM) 3.0 else 1.0) else speed)
        }
        val strideRuns = measureStride(base, iv, Gait.RUN, hasSteps)
        val strideWalks = measureStride(base, iv, Gait.WALK, hasSteps)

        // ---- step fill ----
        for (i in 1 until n) {
            val it = iv[i]
            if (!it.fill) continue
            val a = base[i - 1]
            val b = base[i]
            val stride = localStride(base, iv, i, it.gait) ?: (if (it.gait == Gait.RUN) strideRuns else strideWalks)
                ?: StrideModel.prior(profile, it.gait)
            val lowerBound = (straight(a, b) - a.acc - b.acc).coerceAtLeast(0.0)
            it.dist = max(it.steps * stride, lowerBound)
        }

        // ---- final gait from cadence + windowed speed, short runs merged ----
        val pre = buildTimeline(base, iv, null, null)
        for (i in 1 until n) {
            val it = iv[i]
            if (it.excluded || it.stopped) continue
            val mid = (base[i - 1].t + base[i].t) / 2
            val cadence = if (hasSteps) stepTimeline.cadenceAround(mid, AnalysisTuning.GAIT_HALF_WINDOW_MILLIS) else null
            val speed = pre.windowSpeed(i, AnalysisTuning.GAIT_HALF_WINDOW_MILLIS, 1_000.0) ?: (it.dist / ((base[i].t - base[i - 1].t).coerceAtLeast(1) / 1000.0))
            it.gait = GaitClassifier.classify(cadence, speed)
        }
        mergeShortGaitRuns(base, iv)

        // ---- expand to output points (step-filled gaps get interpolated points) ----
        val out = expand(base, iv, projection, stepTimeline)

        // ---- elevation ----
        val outTimes = LongArray(out.size) { out[it].t }
        val gpsAlt = Array(out.size) { idx -> out[idx].raw?.let { altitudeOf(it) } }
        val pressure = input.sensorSamples.filter { it.type == SensorType.PRESSURE }.map { it.timeMillis to it.value }
        val elevation = ElevationBuilder.build(outTimes, gpsAlt, pressure)
        val gain = DoubleArray(out.size)
        val loss = DoubleArray(out.size)
        elevation.values?.let { values ->
            val counter = ClimbCounter(elevation.threshold)
            for (i in out.indices) {
                if (out[i].kind == null) counter.restart()
                // GPS altitude wanders while standing still; a stop cannot climb.
                if (elevation.source == ElevationSource.GPS && out[i].kind == SegmentKind.STOP) continue
                val (g, l) = counter.add(values[i])
                gain[i] = g; loss[i] = l
            }
        }
        val tl = Timeline(
            time = outTimes,
            distance = DoubleArray(out.size).also { d -> for (i in 1 until out.size) d[i] = d[i - 1] + out[i].dist },
            moving = DoubleArray(out.size).also { m ->
                for (i in 1 until out.size) {
                    val k = out[i].kind
                    m[i] = m[i - 1] + if (k == SegmentKind.RUN || k == SegmentKind.WALK) (out[i].t - out[i - 1].t).toDouble() else 0.0
                }
            },
            kind = Array(out.size) { out[it].kind },
            steps = DoubleArray(out.size) { out[it].steps },
            gain = gain, loss = loss,
        )

        // ---- per-point output ----
        val filtered = out.mapIndexed { i, o ->
            val speed = tl.windowSpeed(i, AnalysisTuning.PACE_HALF_WINDOW_MILLIS)
            val kind = o.kind ?: out.getOrNull(i + 1)?.kind ?: SegmentKind.RUN
            val pace = if (o.kind == SegmentKind.STOP || speed == null) null else paceSecPerKm(speed)
            TrackPoint(
                runId = input.runId, kind = PointKind.FILTERED,
                engine = o.raw?.engine, isPrimary = true,
                timeMillis = o.t, elapsedRealtimeNanos = o.raw?.elapsedRealtimeNanos,
                lat = o.lat, lon = o.lon,
                altitudeM = o.raw?.altitudeM, mslAltitudeM = o.raw?.mslAltitudeM,
                horizontalAccuracyM = o.raw?.horizontalAccuracyM, verticalAccuracyM = o.raw?.verticalAccuracyM,
                speedMps = if (o.kind == SegmentKind.STOP) 0f else speed?.toFloat(),
                speedAccuracyMps = o.raw?.speedAccuracyMps, bearingDeg = o.raw?.bearingDeg,
                paused = false, stepFilled = o.stepFilled, distanceM = tl.distance[i],
                elevationM = elevation.values?.get(i), paceSecPerKm = pace, segmentKind = kind,
            )
        }

        val withPaused = mergePausedPoints(filtered, input.rawPoints, pauses, input.runId)

        // ---- stats ----
        val elapsed = end - start
        val paused = pauses.sumOf { (minOf(it.endMillis ?: end, end) - maxOf(it.startMillis, start)).coerceAtLeast(0) }
            .coerceAtMost(elapsed)
        val movingMillis = tl.totalMoving.roundToLong()
        val distance = tl.totalDistance
        var walkDist = 0.0
        var runDist = 0.0
        var kcal = 0.0
        var stepFilledM = 0.0
        for (i in 1 until out.size) {
            val d = out[i].dist
            if (out[i].stepFilled) stepFilledM += d
            val gait = when (out[i].kind) { SegmentKind.WALK -> Gait.WALK; SegmentKind.RUN -> Gait.RUN; else -> null } ?: continue
            if (gait == Gait.WALK) walkDist += d else runDist += d
            val climb = elevation.values?.let { (it[i] - it[i - 1]).coerceAtLeast(0.0) } ?: 0.0
            kcal += Calories.activityKcal(profile, gait, d, climb)
        }
        kcal += Calories.restingKcal(profile, movingMillis)
        val walkFraction = if (distance > 0) walkDist / distance else null
        val type = input.manualType
            ?: if ((walkFraction ?: 0.0) > AnalysisTuning.WALK_TYPE_FRACTION) ActivityType.WALK else ActivityType.RUN
        val totalSteps = if (hasSteps) stepTimeline.stepsBetween(start, end).roundToInt() else null
        var maxSpeed: Double? = null
        for (i in out.indices) {
            val s = tl.windowSpeed(i, AnalysisTuning.MAX_SPEED_WINDOW_MILLIS / 2, AnalysisTuning.MAX_SPEED_WINDOW_MILLIS - 1_000.0) ?: continue
            if (s < AnalysisTuning.MAX_SPEED_MPS && (maxSpeed == null || s > maxSpeed)) maxSpeed = s
        }
        val dominant = if (walkDist > runDist) Gait.WALK else Gait.RUN
        val learnedRun = StrideModel.learn(profile.learnedRunStrideM, strideRuns)
        val learnedWalk = StrideModel.learn(profile.learnedWalkStrideM, strideWalks)
        val hasElevation = elevation.values != null
        val stats = RunStats(
            distanceM = distance,
            elapsedMillis = elapsed,
            movingMillis = movingMillis,
            pausedMillis = paused,
            avgPaceSecPerKm = paceSecPerKm(distance, movingMillis),
            maxSpeedMps = maxSpeed,
            elevationGainM = if (hasElevation) gain.sum() else null,
            elevationLossM = if (hasElevation) loss.sum() else null,
            elevationSource = elevation.source,
            caloriesKcal = if (out.size >= 2) kcal else null,
            steps = totalSteps,
            avgCadenceSpm = if (totalSteps != null && movingMillis > 0) tl.steps.sum() * 60_000.0 / movingMillis else null,
            walkFraction = walkFraction,
            stepFilledM = stepFilledM,
            bounds = GeoBounds.of(filtered.map { GeoPoint(it.lat, it.lon) }),
            weightKgUsed = profile.weightKg,
            strideMUsed = if (hasSteps) (if (dominant == Gait.RUN) strideRuns else strideWalks) ?: StrideModel.prior(profile, dominant) else null,
        )
        return RunAnalysis(
            stats = stats,
            suggestedType = type,
            suggestedName = namer.name(start, input.zoneId, type),
            filteredPoints = withPaused,
            splits = SplitCalculator.compute(tl, hasElevation, hasSteps),
            segments = SegmentBuilder.compute(tl, hasSteps),
            bestEfforts = BestEffortCalculator.compute(tl),
            learnedRunStrideM = learnedRun,
            learnedWalkStrideM = learnedWalk,
            analysisVersion = AnalysisTuning.ANALYSIS_VERSION,
        )
    }

    // ------------------------------------------------------------------------------------------

    private fun inPause(t: Long, pauses: List<PauseInterval>) = pauses.any { t >= it.startMillis && t < (it.endMillis ?: Long.MAX_VALUE) }

    private fun acceptFixes(raw: List<TrackPoint>, pauses: List<PauseInterval>): List<Base> {
        val filter = GpsPreFilter()
        val out = ArrayList<Base>(raw.size)
        var sawPause = false
        for (p in raw.sortedBy { it.timeMillis }) {
            if (p.paused || inPause(p.timeMillis, pauses)) {
                sawPause = true
                filter.reset()
                continue
            }
            if (filter.check(p.timeMillis, p.lat, p.lon, p.horizontalAccuracyM, p.speedMps) != FixVerdict.ACCEPTED) continue
            val prev = out.lastOrNull()
            val spansPause = prev != null && pauses.any { it.startMillis < p.timeMillis && (it.endMillis ?: Long.MAX_VALUE) > prev.t }
            out += Base(p, excludedBefore = prev == null || sawPause || spansPause)
            sawPause = false
        }
        return out
    }

    private fun smoothPositions(base: List<Base>, projection: LocalProjection) {
        var from = 0
        for (i in 1..base.size) {
            val breakHere = i == base.size || base[i].excludedBefore ||
                base[i].t - base[i - 1].t > AnalysisTuning.KALMAN_RESET_GAP_MILLIS
            if (!breakHere) continue
            val stretch = base.subList(from, i)
            val smoothed = GpsKalman.smooth(stretch.map { p ->
                KalmanMeasurement(
                    timeMillis = p.t, east = projection.east(p.raw.lon), north = projection.north(p.raw.lat),
                    accuracyM = p.acc, speedMps = p.doppler, speedAccuracyMps = p.raw.speedAccuracyMps?.toDouble(),
                    bearingDeg = p.raw.bearingDeg?.toDouble(),
                )
            })
            stretch.forEachIndexed { k, p ->
                p.east = smoothed[k].east
                p.north = smoothed[k].north
                p.kalmanSpeed = hypot(smoothed[k].vEast, smoothed[k].vNorth)
            }
            from = i
        }
    }

    /** Smoothed displacement speed over ±10 s inside one pause-free block. */
    private fun computeWindowSpeeds(base: List<Base>) {
        var blockStart = 0
        for (i in base.indices) {
            if (base[i].excludedBefore) blockStart = i
            var blockEnd = i
            while (blockEnd + 1 < base.size && !base[blockEnd + 1].excludedBefore &&
                base[blockEnd + 1].t - base[i].t <= AnalysisTuning.WINDOW_SPEED_HALF_MILLIS) blockEnd++
            var lo = i
            while (lo > blockStart && base[i].t - base[lo - 1].t <= AnalysisTuning.WINDOW_SPEED_HALF_MILLIS) lo--
            val dt = (base[blockEnd].t - base[lo].t) / 1000.0
            base[i].windowSpeed = if (dt <= 0) base[i].kalmanSpeed
            else hypot(base[blockEnd].east - base[lo].east, base[blockEnd].north - base[lo].north) / dt
        }
    }

    private fun isStationary(p: Base, hasSteps: Boolean, steps: StepTimeline): Boolean {
        val weak = p.acc > AnalysisTuning.WEAK_ACCURACY_M
        val gpsSpeed = if (p.dopplerOk) p.doppler!! else p.kalmanSpeed
        return if (hasSteps) {
            val cadence = steps.cadenceAround(p.t, AnalysisTuning.GATING_CADENCE_HALF_WINDOW_MILLIS)
            if (cadence < AnalysisTuning.STILL_CADENCE_SPM) {
                // Not stepping: still unless good GPS clearly says otherwise (e.g. steps not detected).
                weak || gpsSpeed < AnalysisTuning.GPS_MOVING_WITHOUT_STEPS_MPS ||
                    p.windowSpeed < AnalysisTuning.GPS_MOVING_WITHOUT_STEPS_MPS
            } else {
                // Stepping without going anywhere (jogging on the spot at a light) is a stop on good GPS.
                !weak && p.windowSpeed < AnalysisTuning.STOP_SPEED_MPS && gpsSpeed < AnalysisTuning.STOP_SPEED_MPS
            }
        } else if (p.doppler != null) {
            p.doppler < AnalysisTuning.STILL_DOPPLER_MPS && p.windowSpeed < AnalysisTuning.GPS_MOVING_WITHOUT_STEPS_MPS
        } else {
            p.windowSpeed < AnalysisTuning.STILL_WINDOW_SPEED_MPS
        }
    }

    /** Short stops become moving, short moving blips between stops become stopped (per pause-free block). */
    private fun debounceStops(base: List<Base>, iv: Array<Interval>) {
        data class Run(val from: Int, val to: Int, val stopped: Boolean, val millis: Long)
        fun runs(): List<Run> {
            val out = mutableListOf<Run>()
            var i = 1
            while (i < iv.size) {
                if (iv[i].excluded) { i++; continue }
                val s = iv[i].stopped
                var j = i
                while (j + 1 < iv.size && !iv[j + 1].excluded && iv[j + 1].stopped == s) j++
                out += Run(i, j, s, base[j].t - base[i - 1].t)
                i = j + 1
            }
            return out
        }
        val first = runs()
        for (k in first.indices) {
            val r = first[k]
            val prevStop = first.getOrNull(k - 1)?.let { it.stopped && it.to == r.from - 1 } ?: false
            val nextStop = first.getOrNull(k + 1)?.let { it.stopped && it.from == r.to + 1 } ?: false
            if (!r.stopped && prevStop && nextStop && r.millis < AnalysisTuning.MIN_MOVING_BLIP_MILLIS) {
                for (x in r.from..r.to) iv[x].stopped = true
            }
        }
        for (r in runs()) {
            if (r.stopped && r.millis < AnalysisTuning.MIN_STOP_MILLIS) for (x in r.from..r.to) iv[x].stopped = false
        }
    }

    /** While stopped the position is held at the stop's mean so jitter draws nothing. */
    private fun freezeStops(base: List<Base>, iv: Array<Interval>) {
        var i = 1
        while (i < iv.size) {
            if (!iv[i].stopped) { i++; continue }
            var j = i
            while (j + 1 < iv.size && iv[j + 1].stopped) j++
            val pts = base.subList(i - 1, j + 1)
            val e = pts.sumOf { it.east } / pts.size
            val nn = pts.sumOf { it.north } / pts.size
            for (p in pts) { p.east = e; p.north = nn }
            i = j + 1
        }
    }

    private fun straight(a: Base, b: Base) = hypot(b.east - a.east, b.north - a.north)

    /** Doppler-integrated distance blended with smoothed-position distance (position only across gaps). */
    private fun gpsDistance(a: Base, b: Base, gap: Boolean): Double {
        val pos = straight(a, b)
        if (gap || !a.dopplerOk || !b.dopplerOk) return pos
        val dop = (a.doppler!! + b.doppler!!) / 2 * ((b.t - a.t) / 1000.0)
        val w = AnalysisTuning.DOPPLER_DISTANCE_WEIGHT
        return w * dop + (1 - w) * pos
    }

    private fun isStrideSource(base: List<Base>, iv: Array<Interval>, i: Int, gait: Gait): Boolean {
        val it = iv[i]
        return !it.excluded && !it.stopped && !it.fill && !it.gap && it.gait == gait &&
            max(base[i - 1].acc, base[i].acc) <= AnalysisTuning.STRIDE_MAX_ACCURACY_M
    }

    private fun measureStride(base: List<Base>, iv: Array<Interval>, gait: Gait, hasSteps: Boolean): Double? {
        if (!hasSteps) return null
        var d = 0.0
        var s = 0.0
        for (i in 1 until iv.size) if (isStrideSource(base, iv, i, gait)) { d += iv[i].dist; s += iv[i].steps }
        return StrideModel.measure(gait, d, s)
    }

    private fun localStride(base: List<Base>, iv: Array<Interval>, i: Int, gait: Gait): Double? {
        val lo = base[i - 1].t - AnalysisTuning.LOCAL_STRIDE_WINDOW_MILLIS
        val hi = base[i].t + AnalysisTuning.LOCAL_STRIDE_WINDOW_MILLIS
        var d = 0.0
        var s = 0.0
        for (k in 1 until iv.size) {
            if (base[k].t < lo || base[k - 1].t > hi) continue
            if (isStrideSource(base, iv, k, gait)) { d += iv[k].dist; s += iv[k].steps }
        }
        return StrideModel.measure(gait, d, s, AnalysisTuning.LOCAL_STRIDE_MIN_STEPS)
    }

    /** Run/walk stretches shorter than 20 s are relabelled to their longer neighbour, shortest first. */
    private fun mergeShortGaitRuns(base: List<Base>, iv: Array<Interval>) {
        val moving = (1 until iv.size).filter { !iv[it].excluded && !iv[it].stopped }
        data class GaitRun(val from: Int, val to: Int, val gait: Gait, val millis: Long)
        repeat(moving.size) {
            val runs = mutableListOf<GaitRun>()
            var k = 0
            while (k < moving.size) {
                val g = iv[moving[k]].gait
                var m = k
                var millis = 0L
                while (m < moving.size && iv[moving[m]].gait == g) {
                    millis += base[moving[m]].t - base[moving[m] - 1].t
                    m++
                }
                runs += GaitRun(k, m - 1, g, millis)
                k = m
            }
            if (runs.size <= 1) return
            val shortest = runs.withIndex().filter { it.value.millis < AnalysisTuning.MIN_GAIT_SEGMENT_MILLIS }
                .minByOrNull { it.value.millis } ?: return
            val prev = runs.getOrNull(shortest.index - 1)
            val next = runs.getOrNull(shortest.index + 1)
            val target = when {
                prev == null -> next!!.gait
                next == null -> prev.gait
                else -> if (prev.millis >= next.millis) prev.gait else next.gait
            }
            for (x in shortest.value.from..shortest.value.to) iv[moving[x]].gait = target
        }
    }

    private class Out(
        val t: Long, val lat: Double, val lon: Double, val raw: TrackPoint?,
        val kind: SegmentKind?, val dist: Double, val steps: Double, val stepFilled: Boolean,
    )

    private fun expand(base: List<Base>, iv: Array<Interval>, projection: LocalProjection?, steps: StepTimeline): List<Out> {
        if (projection == null) return emptyList()
        val out = ArrayList<Out>(base.size)
        for (i in base.indices) {
            val b = base[i]
            val geo = projection.toGeo(b.east, b.north)
            val it = iv[i]
            val kind = when {
                it.excluded -> null
                it.stopped -> SegmentKind.STOP
                else -> it.gait.toSegmentKind()
            }
            val dist = if (it.excluded || it.stopped) 0.0 else it.dist
            if (i > 0 && it.fill && it.gap) {
                // Insert evenly timed points across the gap; distance follows the steps taken.
                val a = base[i - 1]
                val dt = b.t - a.t
                val count = (dt / AnalysisTuning.FILL_POINT_SPACING_MILLIS).toInt() - 1
                var prevT = a.t
                var prevFrac = 0.0
                for (k in 1..count) {
                    val t = a.t + dt * k / (count + 1)
                    val frac = if (it.steps > 0) steps.stepsBetween(a.t, t) / it.steps else k.toDouble() / (count + 1)
                    val g = projection.toGeo(a.east + (b.east - a.east) * frac, a.north + (b.north - a.north) * frac)
                    out += Out(t, g.lat, g.lon, null, kind, dist * (frac - prevFrac), steps.stepsBetween(prevT, t), true)
                    prevT = t
                    prevFrac = frac
                }
                out += Out(b.t, geo.lat, geo.lon, b.raw, kind, dist * (1 - prevFrac), steps.stepsBetween(prevT, b.t), true)
            } else {
                out += Out(b.t, geo.lat, geo.lon, b.raw, kind, dist, if (kind == null) 0.0 else it.steps, it.fill && kind != SegmentKind.STOP)
            }
        }
        return out
    }

    private fun buildTimeline(base: List<Base>, iv: Array<Interval>, gain: DoubleArray?, loss: DoubleArray?): Timeline {
        val n = base.size
        val d = DoubleArray(n)
        val m = DoubleArray(n)
        for (i in 1 until n) {
            val moving = !iv[i].excluded && !iv[i].stopped
            d[i] = d[i - 1] + if (moving) iv[i].dist else 0.0
            m[i] = m[i - 1] + if (moving) (base[i].t - base[i - 1].t).toDouble() else 0.0
        }
        return Timeline(
            time = LongArray(n) { base[it].t }, distance = d, moving = m,
            kind = Array(n) { if (iv[it].excluded) null else if (iv[it].stopped) SegmentKind.STOP else iv[it].gait.toSegmentKind() },
            steps = DoubleArray(n) { iv[it].steps }, gain = gain ?: DoubleArray(n), loss = loss ?: DoubleArray(n),
        )
    }

    /**
     * Fixes recorded while paused are kept in the FILTERED track flagged `paused = true` (raw
     * position, distance held at its pre-pause value, no pace or segment) so the full track survives;
     * they never contribute distance, time or segments. Fixes worse than 30 m are dropped as usual.
     */
    private fun mergePausedPoints(filtered: List<TrackPoint>, raw: List<TrackPoint>, pauses: List<PauseInterval>, runId: String): List<TrackPoint> {
        val paused = raw.filter { p ->
            (p.paused || inPause(p.timeMillis, pauses)) &&
                (p.horizontalAccuracyM?.toDouble() ?: AnalysisTuning.DEFAULT_ACCURACY_M) <= AnalysisTuning.MAX_ACCURACY_M
        }
        if (paused.isEmpty()) return filtered
        val out = ArrayList<TrackPoint>(filtered.size + paused.size)
        var i = 0
        var lastDistance = 0.0
        for (p in paused.sortedBy { it.timeMillis }) {
            while (i < filtered.size && filtered[i].timeMillis <= p.timeMillis) {
                lastDistance = filtered[i].distanceM ?: lastDistance
                out += filtered[i++]
            }
            out += p.copy(
                runId = runId, kind = PointKind.FILTERED, paused = true, stepFilled = false, distanceM = lastDistance,
                elevationM = null, paceSecPerKm = null, segmentKind = null,
            )
        }
        while (i < filtered.size) out += filtered[i++]
        return out
    }

    private fun altitudeOf(p: TrackPoint): Double? {
        val vAcc = p.verticalAccuracyM
        if (vAcc != null && vAcc > AnalysisTuning.GPS_ALT_MAX_VERTICAL_ACCURACY_M) return null
        return p.mslAltitudeM ?: p.altitudeM
    }
}
