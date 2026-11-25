package com.example.running.recording

import com.example.running.core.contracts.LiveMetrics
import com.example.running.core.contracts.LiveMetricsFactory
import com.example.running.core.model.LiveStats
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.model.MapRoute
import com.example.running.core.model.PressureSample
import com.example.running.core.model.RoutePoint
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.StepEvent
import com.example.running.core.util.Clock
import com.example.running.core.util.haversineMeters
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.time.ZoneId

const val T0 = 1_764_040_800_000L

/**
 * Clock that follows the test scheduler's virtual time, so every coroutine sees the same "now".
 * [bootedAtMillis] is the wall time elapsed realtime counts from (default: virtual time 0).
 */
class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val startMillis: Long = T0,
    private val bootedAtMillis: Long = startMillis,
) : Clock {
    override fun nowMillis(): Long = startMillis + scheduler.currentTime
    override fun elapsedRealtimeNanos(): Long = (startMillis - bootedAtMillis + scheduler.currentTime) * 1_000_000L
    override fun zoneId(): ZoneId = ZoneId.of("Asia/Kolkata")
}

/**
 * Fixes 1 s apart heading north at [speedMps], times from [startMillis] (match [SchedulerClock] by
 * starting collection at virtual time startMillis - T0).
 */
fun straightFixes(
    count: Int,
    engine: LocationEngine = LocationEngine.PLAY,
    accuracyM: Float? = 5f,
    startMillis: Long = T0,
    speedMps: Double = 3.0,
    lonOffset: Double = 0.0,
): List<LocationFix> = (0 until count).map { i ->
    LocationFix(
        timeMillis = startMillis + i * 1_000L,
        elapsedRealtimeNanos = (startMillis - T0 + i * 1_000L) * 1_000_000L,
        lat = 12.9716 + i * speedMps / 111_320.0,
        lon = 77.5946 + lonOffset,
        altitudeM = 900.0, mslAltitudeM = null,
        horizontalAccuracyM = accuracyM, verticalAccuracyM = null,
        speedMps = speedMps.toFloat(), speedAccuracyMps = null, bearingDeg = 0f,
        engine = engine,
    )
}

/** Deterministic stand-in for B's live metrics: haversine sum of non-paused primary fixes. */
class FakeLiveMetrics(private val startedAtMillis: Long) : LiveMetrics {
    val fixes = mutableListOf<Pair<LocationFix, Boolean>>()
    val steps = mutableListOf<Pair<StepEvent, Boolean>>()
    val pressure = mutableListOf<PressureSample>()
    val pauseChanges = mutableListOf<Pair<Boolean, Long>>()
    val resumeGaps = mutableListOf<Pair<Long, Double?>>()
    var distance = 0.0
        private set
    private var last: LocationFix? = null
    private var lastPaused = false
    private var pausedSince: Long? = null
    private var pausedTotal = 0L

    override fun onFix(fix: LocationFix, paused: Boolean) {
        fixes += fix to paused
        val prev = last
        if (prev != null && !paused && !lastPaused) distance += haversineMeters(prev.lat, prev.lon, fix.lat, fix.lon)
        last = fix
        lastPaused = paused
    }

    override fun onSteps(event: StepEvent, paused: Boolean) {
        steps += event to paused
    }

    override fun onPressure(sample: PressureSample) {
        pressure += sample
    }

    override fun onPauseChanged(paused: Boolean, atMillis: Long) {
        pauseChanges += paused to atMillis
        if (paused) pausedSince = atMillis else pausedSince?.let { pausedTotal += atMillis - it; pausedSince = null }
    }

    override fun onResumeGap(atMillis: Long, steps: Double?) {
        resumeGaps += atMillis to steps
    }

    override fun snapshot(nowMillis: Long): LiveStats {
        val elapsed = nowMillis - startedAtMillis
        val paused = pausedTotal + (pausedSince?.let { nowMillis - it } ?: 0)
        return LiveStats.ZERO.copy(
            elapsedMillis = elapsed,
            movingMillis = elapsed - paused,
            pausedMillis = paused,
            distanceM = distance,
            steps = steps.filterNot { it.second }.sumOf { it.first.count },
        )
    }

    override fun acceptedRoute(): MapRoute =
        MapRoute(fixes.filterNot { it.second }.map { RoutePoint(it.first.lat, it.first.lon, 0.0, null, false, null) })

    /** Paused fixes add no route point. */
    override fun routeVersion(): Int = fixes.count { !it.second }
}

class FakeLiveMetricsFactory : LiveMetricsFactory {
    val created = mutableListOf<FakeLiveMetrics>()
    override fun create(profile: RunnerProfile, startedAtMillis: Long): LiveMetrics =
        FakeLiveMetrics(startedAtMillis).also { created += it }
}

internal class FakeRecordingPlatform(
    var backgroundLocation: Boolean = false,
    var serviceAllowed: Boolean = true,
) : RecordingPlatform {
    var serviceStarts = 0
    var alertsPosted = 0
    var alertsCancelled = 0

    override fun hasBackgroundLocation(): Boolean = backgroundLocation
    override fun startRecordingService(): Boolean {
        serviceStarts++
        return serviceAllowed
    }
    override fun postRecoveryAlert() {
        alertsPosted++
    }
    override fun cancelRecoveryAlert() {
        alertsCancelled++
    }
    override fun text(message: RecordingMessage): String = message.name
}

internal class InMemoryRecoveryStore : RecoveryStore {
    private var runId: String? = null
    private var attempts = 0
    override fun autoResumeAttempts(runId: String): Int = if (this.runId == runId) attempts else 0
    override fun recordAutoResume(runId: String) {
        attempts = autoResumeAttempts(runId) + 1
        this.runId = runId
    }
    override fun clear() {
        runId = null
        attempts = 0
    }
}
