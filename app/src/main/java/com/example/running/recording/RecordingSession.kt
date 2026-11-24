package com.example.running.recording

import com.example.running.core.contracts.LiveMetrics
import com.example.running.core.model.LiveStats
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.model.MapRoute
import com.example.running.core.model.PressureSample
import com.example.running.core.model.SensorSample
import com.example.running.core.model.SensorType
import com.example.running.core.model.StepCounterReading
import com.example.running.core.model.StepEvent
import com.example.running.core.model.TrackPoint
import com.example.running.data.repo.RunRepository

/**
 * One run being recorded: feeds B's [LiveMetrics], buffers RAW points and sensor samples and writes
 * them to the repository in batches. Not thread-safe: the controller confines it to one serial dispatcher.
 *
 * Pauses: GPS keeps running; fixes while paused are stored with `paused = true` and handed to the live
 * metrics flagged as paused, so paused movement is never counted.
 *
 * Step counter: the latest raw (since-boot) reading is stored with each flush as STEP_COUNTER_BOOT, the
 * anchor for the next interruption. After a resume ([resumeGap] non-null) the first reading gives the
 * steps taken while the app was dead; the session then writes a RESUME_GAP sample and calls
 * [LiveMetrics.onResumeGap]. Until then (at most [GAP_COUNTER_WAIT_MILLIS]) events are stored as usual
 * but held back from the live metrics, so they always see the gap before anything recorded after it.
 */
internal class RecordingSession(
    val runId: String,
    val startedAtMillis: Long,
    private val dualEngine: Boolean,
    private val stepsAreDetectorEvents: Boolean,
    private val metrics: LiveMetrics,
    private val repository: RunRepository,
    initiallyPaused: Boolean = false,
    initialStepCount: Double = 0.0,
    /** Fixes stamped before this are dropped (warm-up fixes; cached fixes older than resumed data). */
    private val notBeforeMillis: Long = startedAtMillis,
    private val hasStepCounter: Boolean = false,
    /** Set when resuming an interrupted run whose gap is not a pause. */
    private val resumeGap: ResumeGapAnchor? = null,
) {
    var paused: Boolean = initiallyPaused
        private set
    var lastFlushMillis: Long = startedAtMillis
        private set

    private val pendingPoints = mutableListOf<TrackPoint>()
    private val pendingSamples = mutableListOf<SensorSample>()
    private var cumulativeSteps = initialStepCount
    private val lastFixTime = mutableMapOf<LocationEngine, Long>()
    private var routeVersion = 0
    private var publishedRouteVersion = -1

    private var gapPending = resumeGap != null
    private val held = mutableListOf<() -> Unit>()
    private var stepsSinceResume = 0.0
    private var latestCounter: StepCounterReading? = null
    private var storedCounterMillis = Long.MIN_VALUE

    init {
        if (resumeGap != null && (!hasStepCounter || resumeGap.unusable())) resolveGap(null)
    }

    /** Hands [block] to the live metrics now, or holds it while the resume gap is unresolved. */
    private fun feed(atMillis: Long, block: () -> Unit) {
        val gap = resumeGap
        if (gapPending && gap != null && atMillis >= gap.resumedAtMillis + GAP_COUNTER_WAIT_MILLIS) resolveGap(null)
        if (gapPending) held += block else block()
    }

    private fun resolveGap(steps: Double?) {
        val gap = resumeGap ?: return
        if (!gapPending) return
        gapPending = false
        pendingSamples += SensorSample(SensorType.RESUME_GAP, gap.resumedAtMillis, steps ?: -1.0)
        metrics.onResumeGap(gap.resumedAtMillis, steps)
        routeVersion++
        held.forEach { it() }
        held.clear()
    }

    fun onCounterReading(reading: StepCounterReading) {
        latestCounter = reading
        val gap = resumeGap
        if (gapPending && gap != null) resolveGap(resumeGapSteps(gap, reading.stepsSinceBoot, reading.timeMillis, stepsSinceResume))
    }

    /** Fixes from before the start (warm-up) and repeated timestamps from an engine are dropped. */
    fun onFix(fix: LocationFix, isPrimary: Boolean) {
        if (fix.timeMillis < notBeforeMillis) return
        val previous = lastFixTime[fix.engine]
        if (previous != null && fix.timeMillis <= previous) return
        lastFixTime[fix.engine] = fix.timeMillis
        val pausedNow = paused
        if (isPrimary) {
            feed(fix.timeMillis) { metrics.onFix(fix, pausedNow) }
            routeVersion++
        }
        if (isPrimary || dualEngine) pendingPoints += fix.toRawPoint(runId, isPrimary = isPrimary, paused = paused)
    }

    fun onSteps(event: StepEvent) {
        if (event.count <= 0) return
        if (gapPending) stepsSinceResume += event.count
        val pausedNow = paused
        feed(event.timeMillis) { metrics.onSteps(event, pausedNow) }
        if (stepsAreDetectorEvents) {
            repeat(event.count) { pendingSamples += SensorSample(SensorType.STEP_DETECTOR, event.timeMillis, 1.0) }
        } else {
            cumulativeSteps += event.count
            pendingSamples += SensorSample(SensorType.STEP_COUNTER, event.timeMillis, cumulativeSteps)
        }
    }

    fun onPressure(sample: PressureSample) {
        feed(sample.timeMillis) { metrics.onPressure(sample) }
        pendingSamples += SensorSample(SensorType.PRESSURE, sample.timeMillis, sample.hPa)
    }

    /**
     * Writes the buffered batches. Items leave the buffer only after their write succeeded, so a
     * failed write (disk full, transient I/O) is retried by the next flush instead of being lost.
     * New items may be appended while a write is suspended; they stay queued behind the batch.
     */
    suspend fun flush(nowMillis: Long, final: Boolean = false) {
        lastFlushMillis = nowMillis
        val gap = resumeGap
        if (gapPending && gap != null && (final || nowMillis >= gap.resumedAtMillis + GAP_COUNTER_WAIT_MILLIS)) resolveGap(null)
        latestCounter?.takeIf { it.timeMillis > storedCounterMillis }?.let {
            pendingSamples += SensorSample(SensorType.STEP_COUNTER_BOOT, it.timeMillis, it.stepsSinceBoot)
            storedCounterMillis = it.timeMillis
        }
        if (pendingPoints.isNotEmpty()) {
            val batch = pendingPoints.toList()
            repository.appendRawPoints(batch)
            pendingPoints.subList(0, batch.size).clear()
        }
        if (pendingSamples.isNotEmpty()) {
            val batch = pendingSamples.toList()
            repository.appendSensorSamples(runId, batch)
            pendingSamples.subList(0, batch.size).clear()
        }
    }

    suspend fun pause(atMillis: Long) {
        if (paused) return
        if (gapPending) resolveGap(null)
        flush(atMillis)
        repository.startPause(runId, atMillis)
        metrics.onPauseChanged(true, atMillis)
        paused = true
    }

    suspend fun resume(atMillis: Long) {
        if (!paused) return
        flush(atMillis)
        repository.endPause(runId, atMillis)
        metrics.onPauseChanged(false, atMillis)
        paused = false
    }

    fun snapshot(nowMillis: Long): LiveStats = metrics.snapshot(nowMillis)

    /** The live route if it changed since the last call, else null. */
    fun routeIfChanged(): MapRoute? {
        if (routeVersion == publishedRouteVersion) return null
        publishedRouteVersion = routeVersion
        return metrics.acceptedRoute()
    }
}
