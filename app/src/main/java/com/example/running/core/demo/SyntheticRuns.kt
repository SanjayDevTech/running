package com.example.running.core.demo

import com.example.running.core.model.ActivityType
import com.example.running.core.model.BestEffort
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.ElevationSource
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.LocationFix
import com.example.running.core.model.PointKind
import com.example.running.core.model.Run
import com.example.running.core.model.RunGraph
import com.example.running.core.model.RunStats
import com.example.running.core.model.RunStatus
import com.example.running.core.model.Segment
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.SensorSample
import com.example.running.core.model.SensorType
import com.example.running.core.model.Split
import com.example.running.core.model.TrackPoint
import com.example.running.core.util.METERS_PER_KM
import com.example.running.core.util.paceSecPerKm
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deterministic synthetic runs (seeded Random) for debug demo seeding and tests. Pure Kotlin.
 * Ground truth is a 1 Hz path; RAW points add position noise, FILTERED points are the truth.
 */
object SyntheticRuns {

    data class Spec(
        val distanceM: Double = 5_000.0,
        val paceSecPerKm: Double = 330.0,
        /** Alternate 4 min running / 1 min walking at 1.4 m/s. */
        val runWalkIntervals: Boolean = false,
        /** All walking at [paceSecPerKm] (use ~720 s/km). */
        val walkOnly: Boolean = false,
        /** One standing stop of this length halfway through. */
        val stopSeconds: Int = 0,
        /** Std-dev of RAW position noise in metres. */
        val noiseM: Double = 3.0,
        val seed: Long = 1L,
        val start: GeoPoint = GeoPoint(12.9716, 77.5946),
        val engine: LocationEngine = LocationEngine.PLAY,
    )

    private data class Sample(
        val t: Long, val lat: Double, val lon: Double, val alt: Double, val speed: Double,
        val dist: Double, val moving: Boolean, val kind: SegmentKind,
    )

    private const val WALK_SPEED = 1.4

    private fun truth(spec: Spec): List<Sample> {
        val rnd = Random(spec.seed)
        val runSpeed = METERS_PER_KM / spec.paceSecPerKm
        var lat = spec.start.lat
        var lon = spec.start.lon
        var bearing = rnd.nextDouble(0.0, 2 * PI)
        var dist = 0.0
        var t = 0L
        var stopLeft = spec.stopSeconds
        var stopped = false
        val out = mutableListOf(Sample(0, lat, lon, 900.0, 0.0, 0.0, true, if (spec.walkOnly) SegmentKind.WALK else SegmentKind.RUN))
        while (dist < spec.distanceM) {
            t += 1000
            val inStop = !stopped && spec.stopSeconds > 0 && dist >= spec.distanceM / 2 && stopLeft > 0
            if (inStop) {
                stopLeft--
                if (stopLeft == 0) stopped = true
                out += out.last().copy(t = t, speed = 0.0, moving = false, kind = SegmentKind.STOP)
                continue
            }
            val walking = spec.walkOnly || (spec.runWalkIntervals && (t / 1000) % 300 >= 240)
            val speed = if (spec.walkOnly) runSpeed else if (walking) WALK_SPEED else runSpeed * (1 + rnd.nextDouble(-0.05, 0.05))
            bearing += rnd.nextDouble(-0.08, 0.08)
            val dLat = speed * cos(bearing) / 111_320.0
            val dLon = speed * sin(bearing) / (111_320.0 * cos(Math.toRadians(lat)))
            lat += dLat
            lon += dLon
            dist += speed
            val alt = 900.0 + 12.0 * sin(dist / 800.0)
            out += Sample(t, lat, lon, alt, speed, dist, true, if (walking) SegmentKind.WALK else SegmentKind.RUN)
        }
        return out
    }

    /** RAW fixes as an engine would deliver them, times relative to [startMillis]. */
    fun fixes(spec: Spec, startMillis: Long): List<LocationFix> {
        val rnd = Random(spec.seed * 31 + 7)
        return truth(spec).map { s ->
            val nLat = rnd.nextGaussian() * spec.noiseM / 111_320.0
            val nLon = rnd.nextGaussian() * spec.noiseM / (111_320.0 * cos(Math.toRadians(s.lat)))
            LocationFix(
                timeMillis = startMillis + s.t,
                elapsedRealtimeNanos = s.t * 1_000_000L,
                lat = s.lat + nLat,
                lon = s.lon + nLon,
                altitudeM = s.alt + 60.0,
                mslAltitudeM = s.alt,
                horizontalAccuracyM = (3.0 + spec.noiseM).toFloat(),
                verticalAccuracyM = 6f,
                speedMps = s.speed.toFloat(),
                speedAccuracyMps = 0.4f,
                bearingDeg = null,
                engine = spec.engine,
            )
        }
    }

    /** A complete FINISHED run with consistent stats, splits, segments and best efforts. */
    fun runGraph(id: String, startMillis: Long, zoneId: String, spec: Spec, name: String? = null): RunGraph {
        val truth = truth(spec)
        val raw = fixes(spec, startMillis).map { it.toRawPoint(id, isPrimary = true, paused = false) }
        val movingPrefix = LongArray(truth.size)
        for (i in 1 until truth.size) movingPrefix[i] = movingPrefix[i - 1] + if (truth[i].moving) 1000 else 0
        val filtered = truth.mapIndexed { i, s ->
            TrackPoint(
                runId = id, kind = PointKind.FILTERED, engine = spec.engine, isPrimary = true,
                timeMillis = startMillis + s.t, elapsedRealtimeNanos = null, lat = s.lat, lon = s.lon,
                altitudeM = null, mslAltitudeM = null, horizontalAccuracyM = null, verticalAccuracyM = null,
                speedMps = s.speed.toFloat(), speedAccuracyMps = null, bearingDeg = null, paused = false,
                distanceM = s.dist, elevationM = s.alt, paceSecPerKm = paceSecPerKm(s.speed), segmentKind = s.kind,
            )
        }
        val elapsed = truth.last().t
        val moving = movingPrefix.last()
        val total = truth.last().dist
        var gain = 0.0
        var loss = 0.0
        for (i in 1 until truth.size) {
            val d = truth[i].alt - truth[i - 1].alt
            if (d > 0) gain += d else loss -= d
        }
        val walkDist = (1 until truth.size).sumOf { if (truth[it].kind == SegmentKind.WALK) truth[it].dist - truth[it - 1].dist else 0.0 }
        val walkFraction = if (total > 0) walkDist / total else 0.0
        val type = if (walkFraction > 0.5) ActivityType.WALK else ActivityType.RUN
        val cadence = if (type == ActivityType.WALK) 110.0 else 168.0
        val run = Run(
            id = id,
            name = name ?: defaultName(startMillis, zoneId, type),
            nameIsCustom = false, type = type, typeIsManual = false, status = RunStatus.FINISHED,
            startedAtMillis = startMillis, endedAtMillis = startMillis + elapsed, zoneId = zoneId,
            engineMode = LocationEngineMode.AUTO, startEngine = spec.engine, engineFallback = false,
            devDualEngine = false,
            stats = RunStats(
                distanceM = total, elapsedMillis = elapsed, movingMillis = moving, pausedMillis = 0,
                avgPaceSecPerKm = paceSecPerKm(total, moving), maxSpeedMps = truth.maxOf { it.speed },
                elevationGainM = gain, elevationLossM = loss, elevationSource = ElevationSource.GPS,
                caloriesKcal = 70.0 * total / METERS_PER_KM * (if (type == ActivityType.WALK) 0.6 else 1.036),
                steps = (cadence * moving / 60_000.0).roundToInt(), avgCadenceSpm = cadence,
                walkFraction = walkFraction, stepFilledM = 0.0,
                bounds = GeoBounds.of(truth.map { GeoPoint(it.lat, it.lon) }),
                weightKgUsed = 70.0, strideMUsed = null,
            ),
            analysisVersion = 0, createdAtMillis = startMillis + elapsed, updatedAtMillis = startMillis + elapsed,
        )
        val steps = (0 until truth.size step 10).filter { truth[it].moving }.map {
            SensorSample(SensorType.STEP_COUNTER, startMillis + truth[it].t, cadence * movingPrefix[it] / 60_000.0)
        }
        return RunGraph(
            run = run,
            points = raw + filtered,
            pauses = emptyList(),
            splits = splits(truth, movingPrefix, startMillis),
            segments = segments(truth, startMillis),
            bestEfforts = bestEfforts(truth, movingPrefix, startMillis),
            sensorSamples = steps,
        )
    }

    /** ~[count] finished runs spread over the last [weeks] weeks, fixed ids "demo-NN" (idempotent import). */
    fun demoGraphs(nowMillis: Long, zone: ZoneId, count: Int = 40, weeks: Int = 16): List<RunGraph> {
        val rnd = Random(42)
        val spanMillis = weeks * 7L * 24 * 3600 * 1000
        return (0 until count).map { i ->
            val dayOffset = spanMillis * (count - i) / count
            val day = Instant.ofEpochMilli(nowMillis - dayOffset).atZone(zone).toLocalDate()
            val hour = listOf(6, 7, 12, 18, 19)[rnd.nextInt(5)]
            val start = day.atTime(hour, rnd.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
            val kind = rnd.nextInt(10)
            val spec = Spec(
                distanceM = when {
                    kind < 2 -> rnd.nextDouble(2_000.0, 4_000.0)
                    kind < 8 -> rnd.nextDouble(4_000.0, 10_500.0)
                    else -> rnd.nextDouble(12_000.0, 22_000.0)
                },
                paceSecPerKm = if (kind < 2) rnd.nextDouble(660.0, 780.0) else rnd.nextDouble(290.0, 400.0),
                walkOnly = kind < 2,
                runWalkIntervals = kind == 2,
                stopSeconds = if (kind == 3) 40 else 0,
                seed = 1000L + i,
            )
            runGraph("demo-%02d".format(i), start, zone.id, spec)
        }
    }

    private fun defaultName(startMillis: Long, zoneId: String, type: ActivityType): String {
        val hour = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of(zoneId)).hour
        val part = when (hour) {
            in 5..10 -> "Morning"; in 11..13 -> "Lunch"; in 14..16 -> "Afternoon"; in 17..20 -> "Evening"; else -> "Night"
        }
        return "$part ${if (type == ActivityType.WALK) "Walk" else "Run"}"
    }

    private fun splits(truth: List<Sample>, moving: LongArray, start: Long): List<Split> {
        val out = mutableListOf<Split>()
        var from = 0
        var nextKm = METERS_PER_KM
        fun add(to: Int) {
            val d = truth[to].dist - truth[from].dist
            val mv = moving[to] - moving[from]
            out += Split(
                index = out.size, distanceM = d, startMillis = start + truth[from].t, endMillis = start + truth[to].t,
                movingMillis = mv, elapsedMillis = truth[to].t - truth[from].t, paceSecPerKm = paceSecPerKm(d, mv),
                elevationGainM = null, elevationLossM = null, avgCadenceSpm = null, walkFraction = null,
            )
            from = to
        }
        for (i in truth.indices) if (truth[i].dist >= nextKm) { add(i); nextKm += METERS_PER_KM }
        if (truth.last().dist - truth[from].dist >= 10.0) add(truth.lastIndex)
        return out
    }

    private fun segments(truth: List<Sample>, start: Long): List<Segment> {
        val out = mutableListOf<Segment>()
        var from = 0
        for (i in 1..truth.size) {
            if (i == truth.size || truth[i].kind != truth[from].kind) {
                val to = i - 1
                val dt = (truth[to].t - truth[from].t).coerceAtLeast(1)
                out += Segment(
                    kind = truth[from].kind, startMillis = start + truth[from].t, endMillis = start + truth[to].t,
                    startDistanceM = truth[from].dist, endDistanceM = truth[to].dist,
                    avgSpeedMps = (truth[to].dist - truth[from].dist) / (dt / 1000.0), avgCadenceSpm = null,
                )
                from = i
            }
        }
        return out
    }

    /** Fastest window (moving time) per distance, two pointers over the 1 Hz truth. */
    private fun bestEfforts(truth: List<Sample>, moving: LongArray, start: Long): List<BestEffort> =
        BestEffortDistance.entries.mapNotNull { d ->
            if (truth.last().dist < d.meters) return@mapNotNull null
            var best: BestEffort? = null
            var j = 0
            for (i in truth.indices) {
                while (j < i && truth[i].dist - truth[j + 1].dist >= d.meters) j++
                if (truth[i].dist - truth[j].dist >= d.meters) {
                    val mv = moving[i] - moving[j]
                    if (best == null || mv < best.durationMillis) {
                        best = BestEffort(d, mv, start + truth[j].t, start + truth[i].t, truth[j].dist)
                    }
                }
            }
            best
        }

}

private fun Random.nextGaussian(): Double {
    // Box–Muller; deterministic for a seeded Random.
    val u1 = nextDouble().coerceAtLeast(1e-12)
    val u2 = nextDouble()
    return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * cos(2 * PI * u2)
}
