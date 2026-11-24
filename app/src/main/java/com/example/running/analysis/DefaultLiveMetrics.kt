package com.example.running.analysis

import com.example.running.core.contracts.LiveMetrics
import com.example.running.core.contracts.LiveMetricsFactory
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.LiveStats
import com.example.running.core.model.LocationFix
import com.example.running.core.model.MapRoute
import com.example.running.core.model.PressureSample
import com.example.running.core.model.RoutePoint
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.StepEvent
import com.example.running.core.util.METERS_PER_KM
import com.example.running.core.util.paceSecPerKm
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal class DefaultLiveMetricsFactory : LiveMetricsFactory {
    override fun create(profile: RunnerProfile, startedAtMillis: Long): LiveMetrics = DefaultLiveMetrics(profile, startedAtMillis)
}

/**
 * Streaming version of the analysis for the Record screen and notification: same pre-filter,
 * forward Kalman (no smoother), step-based stationary gating, step distance while GPS is weak
 * (> 20 m) or missing, live moving time, pace, cadence, calories and elevation gain.
 * Thread-safe: every entry point is synchronized (fixes, steps and snapshots may come from
 * different threads).
 */
internal class DefaultLiveMetrics(
    private val profile: RunnerProfile,
    private val startedAtMillis: Long,
) : LiveMetrics {

    private class LastFix(val t: Long, val east: Double, val north: Double, val acc: Double, val doppler: Double?, val dopplerOk: Boolean)
    private data class Sample(val t: Long, val distance: Double, val moving: Long)

    private val filter = GpsPreFilter()
    private val kalman = GpsKalman()
    private var projection: LocalProjection? = null
    private var last: LastFix? = null

    private var paused = false
    private var pausedSince: Long? = null
    private var pausedTotal = 0L

    private var distance = 0.0
    private var pendingSteps = 0.0
    private var provisional = 0.0

    private var stepSensorSeen = false
    private var totalSteps = 0
    private val stepEvents = ArrayDeque<StepEvent>()

    private var movingMillis = 0L
    private var lastEventTime: Long? = null
    private var movingNow = false
    private var stationaryNow = false

    private val history = ArrayDeque<Sample>()
    private val route = ArrayList<RoutePoint>()
    private var nextKm = METERS_PER_KM
    private var lastSplitMoving = 0L
    private var lastSplitPace: Double? = null

    private var activityKcal = 0.0

    private var baroSeen = false
    private var baroEma: Double? = null
    private var gpsAltEma: Double? = null
    private val baroClimb = ClimbCounter(AnalysisTuning.BARO_HYSTERESIS_M)
    private val gpsClimb = ClimbCounter(AnalysisTuning.GPS_ALT_HYSTERESIS_M)
    private var lastClimbGain = 0.0

    // Live stride measurement from good-GPS stretches.
    private val goodDist = DoubleArray(2)
    private val goodSteps = DoubleArray(2)

    @Synchronized
    override fun onFix(fix: LocationFix, paused: Boolean) {
        if (paused || this.paused) {
            breakContinuity()
            return
        }
        if (filter.check(fix.timeMillis, fix.lat, fix.lon, fix.horizontalAccuracyM, fix.speedMps) != FixVerdict.ACCEPTED) return
        accrueMoving(fix.timeMillis)
        val proj = projection ?: LocalProjection(GeoPoint(fix.lat, fix.lon)).also { projection = it }
        val acc = fix.horizontalAccuracyM?.toDouble() ?: AnalysisTuning.DEFAULT_ACCURACY_M
        val doppler = fix.speedMps?.toDouble()
        val dopplerOk = doppler != null && (fix.speedAccuracyMps?.toDouble() ?: 0.5) <= AnalysisTuning.DOPPLER_MAX_SPEED_ACCURACY
        val prev = last
        if (prev != null && fix.timeMillis - prev.t > AnalysisTuning.KALMAN_RESET_GAP_MILLIS) kalman.reset()
        val k = kalman.step(
            KalmanMeasurement(
                fix.timeMillis, proj.east(fix.lon), proj.north(fix.lat), acc, doppler,
                fix.speedAccuracyMps?.toDouble(), fix.bearingDeg?.toDouble(),
            )
        ).second
        val weak = acc > AnalysisTuning.WEAK_ACCURACY_M
        val gpsSpeed = if (dopplerOk) doppler!! else hypot(k.vEast, k.vNorth)
        val stepping = stepSensorSeen && recentCadence(fix.timeMillis, AnalysisTuning.LIVE_STILL_NO_STEPS_MILLIS) >= AnalysisTuning.STILL_CADENCE_SPM
        val stationary = when {
            stepSensorSeen && !stepping -> weak || gpsSpeed < AnalysisTuning.GPS_MOVING_WITHOUT_STEPS_MPS
            stepSensorSeen -> !weak && gpsSpeed < AnalysisTuning.STOP_SPEED_MPS
            doppler != null -> doppler < AnalysisTuning.STILL_DOPPLER_MPS
            else -> hypot(k.vEast, k.vNorth) < AnalysisTuning.STILL_DOPPLER_MPS
        }
        updateGpsElevation(fix)

        var stepFilled = false
        var increment = 0.0
        val gait = currentGait(fix.timeMillis, gpsSpeed)
        if (prev != null) {
            val dt = fix.timeMillis - prev.t
            val straight = hypot(k.east - prev.east, k.north - prev.north)
            val fill = stepSensorSeen && (weak || prev.acc > AnalysisTuning.WEAK_ACCURACY_M || dt > AnalysisTuning.GAP_MILLIS)
            when {
                fill && !(stationary && pendingSteps == 0.0) -> {
                    val target = max(pendingSteps * stride(gait), (straight - acc - prev.acc).coerceAtLeast(0.0))
                    increment = (target - provisional).coerceAtLeast(0.0)
                    stepFilled = true
                }
                stationary -> increment = 0.0
                dt > AnalysisTuning.GAP_MILLIS || !dopplerOk || !prev.dopplerOk -> increment = straight
                else -> {
                    val dop = (doppler!! + prev.doppler!!) / 2 * (dt / 1000.0)
                    val w = AnalysisTuning.DOPPLER_DISTANCE_WEIGHT
                    increment = w * dop + (1 - w) * straight
                }
            }
            if (!fill && !stationary && dt <= AnalysisTuning.GAP_MILLIS && max(acc, prev.acc) <= AnalysisTuning.STRIDE_MAX_ACCURACY_M) {
                goodDist[gait.ordinal] += increment
                goodSteps[gait.ordinal] += pendingSteps
            }
        }
        addDistance(increment, gait)
        pendingSteps = 0.0
        provisional = 0.0
        stationaryNow = stationary
        movingNow = !stationary
        last = LastFix(fix.timeMillis, k.east, k.north, acc, doppler, dopplerOk)
        history.addLast(Sample(fix.timeMillis, distance, movingMillis))
        while (history.size > 2 && fix.timeMillis - history.first().t > AnalysisTuning.LIVE_PACE_WINDOW_MILLIS) history.removeFirst()
        if (!stationary || route.isEmpty()) {
            val geo = proj.toGeo(k.east, k.north)
            route += RoutePoint(
                geo.lat, geo.lon, distance, currentPace(), stepFilled,
                if (stationary) SegmentKind.STOP else gait.toSegmentKind(),
            )
        }
    }

    @Synchronized
    override fun onSteps(event: StepEvent, paused: Boolean) {
        stepSensorSeen = true
        if (paused || this.paused) return
        accrueMoving(event.timeMillis)
        totalSteps += event.count
        pendingSteps += event.count
        stepEvents.addLast(event)
        while (stepEvents.isNotEmpty() && event.timeMillis - stepEvents.first().timeMillis > 60_000) stepEvents.removeFirst()
        val prev = last
        val outage = prev == null || event.timeMillis - prev.t > AnalysisTuning.GAP_MILLIS || prev.acc > AnalysisTuning.WEAK_ACCURACY_M
        if (outage && prev != null) {
            // GPS is missing or weak: distance follows the steps immediately, reconciled at the next fix.
            val gait = currentGait(event.timeMillis, null)
            val target = pendingSteps * stride(gait)
            val add = (target - provisional).coerceAtLeast(0.0)
            provisional += add
            addDistance(add, gait)
            movingNow = recentCadence(event.timeMillis, AnalysisTuning.LIVE_STILL_NO_STEPS_MILLIS) >= AnalysisTuning.STILL_CADENCE_SPM
        }
    }

    @Synchronized
    override fun onPressure(sample: PressureSample) {
        if (paused || sample.hPa !in 300.0..1_100.0) return
        baroSeen = true
        val alt = pressureToAltitudeM(sample.hPa)
        val ema = baroEma?.let { it + 0.2 * (alt - it) } ?: alt
        baroEma = ema
        baroClimb.add(ema)
        addClimbKcal()
    }

    @Synchronized
    override fun onPauseChanged(paused: Boolean, atMillis: Long) {
        if (paused == this.paused) return
        if (paused) {
            accrueMoving(atMillis)
            pausedSince = atMillis
            this.paused = true
        } else {
            pausedSince?.let { pausedTotal += (atMillis - it).coerceAtLeast(0) }
            pausedSince = null
            this.paused = false
            lastEventTime = atMillis
            movingNow = false
        }
        breakContinuity()
    }

    @Synchronized
    override fun snapshot(nowMillis: Long): LiveStats {
        val elapsed = (nowMillis - startedAtMillis).coerceAtLeast(0)
        val moving = movingMillis + pendingAccrual(nowMillis)
        val lastT = last?.t
        val pace = if (stationaryNow || lastT == null || nowMillis - lastT > AnalysisTuning.LIVE_PACE_WINDOW_MILLIS) null else currentPace()
        return LiveStats(
            elapsedMillis = elapsed,
            movingMillis = moving,
            distanceM = distance,
            currentPaceSecPerKm = pace,
            avgPaceSecPerKm = paceSecPerKm(distance, moving),
            caloriesKcal = activityKcal + Calories.restingKcal(profile, moving),
            steps = if (stepSensorSeen) totalSteps else null,
            cadenceSpm = if (stepSensorSeen) recentCadence(nowMillis, AnalysisTuning.LIVE_CADENCE_WINDOW_MILLIS) else null,
            elevationGainM = elevationGain(),
            currentSplitIndex = (distance / METERS_PER_KM).toInt(),
            lastSplitPaceSecPerKm = lastSplitPace,
            pausedMillis = pausedTotal + (pausedSince?.let { (nowMillis - it).coerceAtLeast(0) } ?: 0L),
        )
    }

    @Synchronized
    override fun acceptedRoute(): MapRoute = MapRoute(ArrayList(route))

    // ------------------------------------------------------------------------------------------

    private fun breakContinuity() {
        filter.reset()
        kalman.reset()
        last = null
        pendingSteps = 0.0
        provisional = 0.0
        history.clear()
        baroClimb.restart()
        gpsClimb.restart()
        baroEma = null
        gpsAltEma = null
    }

    private fun pendingAccrual(now: Long): Long {
        val t = lastEventTime ?: return 0
        if (paused || !movingNow) return 0
        return (now - t).coerceIn(0, AnalysisTuning.LIVE_MAX_ACCRUAL_MILLIS)
    }

    private fun accrueMoving(t: Long) {
        movingMillis += pendingAccrual(t)
        lastEventTime = max(t, lastEventTime ?: t)
    }

    private fun addDistance(d: Double, gait: Gait) {
        if (d <= 0) return
        distance += d
        activityKcal += Calories.activityKcal(profile, gait, d, 0.0)
        while (distance >= nextKm) {
            lastSplitPace = (movingMillis - lastSplitMoving) / 1000.0
            lastSplitMoving = movingMillis
            nextKm += METERS_PER_KM
        }
    }

    private fun addClimbKcal() {
        val g = elevationGain() ?: return
        val delta = g - lastClimbGain
        if (delta > 0) {
            // Only the vertical term: the horizontal cost was already counted per metre.
            activityKcal += Calories.climbKcal(profile, Gait.RUN, delta)
        }
        lastClimbGain = g
    }

    private fun elevationGain(): Double? = when {
        baroSeen -> baroClimb.gain
        gpsAltEma != null || gpsClimb.gain > 0 -> gpsClimb.gain
        else -> null
    }

    private fun updateGpsElevation(fix: LocationFix) {
        if (baroSeen) return
        val vAcc = fix.verticalAccuracyM
        if (vAcc != null && vAcc > AnalysisTuning.GPS_ALT_MAX_VERTICAL_ACCURACY_M) return
        val alt = fix.mslAltitudeM ?: fix.altitudeM ?: return
        val ema = gpsAltEma?.let { it + 0.1 * (alt - it) } ?: alt
        gpsAltEma = ema
        gpsClimb.add(ema)
        addClimbKcal()
    }

    private fun recentCadence(now: Long, window: Long): Double {
        var n = 0
        for (e in stepEvents) if (e.timeMillis > now - window && e.timeMillis <= now) n += e.count
        return n * 60_000.0 / window
    }

    private fun currentGait(now: Long, gpsSpeed: Double?): Gait {
        val cadence = if (stepSensorSeen) recentCadence(now, AnalysisTuning.GAIT_HALF_WINDOW_MILLIS * 2) else null
        val speed = gpsSpeed ?: currentSpeed() ?: 0.0
        return GaitClassifier.classify(cadence, speed)
    }

    private fun stride(gait: Gait): Double =
        StrideModel.measure(gait, goodDist[gait.ordinal], goodSteps[gait.ordinal], AnalysisTuning.LOCAL_STRIDE_MIN_STEPS)
            ?: StrideModel.prior(profile, gait)

    private fun currentSpeed(): Double? {
        if (history.size < 2) return null
        val a = history.first()
        val b = history.last()
        val m = b.moving - a.moving
        if (m < 3_000 || b.distance - a.distance < 5.0) return null
        return (b.distance - a.distance) / (m / 1000.0)
    }

    private fun currentPace(): Double? = currentSpeed()?.let { paceSecPerKm(it) }?.let { min(it, 3_599.0) }
}
