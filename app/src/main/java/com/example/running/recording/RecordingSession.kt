package com.example.running.recording

import com.example.running.core.contracts.LiveMetrics
import com.example.running.core.model.LiveStats
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.model.MapRoute
import com.example.running.core.model.PressureSample
import com.example.running.core.model.SensorSample
import com.example.running.core.model.SensorType
import com.example.running.core.model.StepEvent
import com.example.running.core.model.TrackPoint
import com.example.running.data.repo.RunRepository

/**
 * One run being recorded: feeds B's [LiveMetrics], buffers RAW points and sensor samples and writes
 * them to the repository in batches. Not thread-safe: the controller confines it to one serial dispatcher.
 *
 * Pauses: GPS keeps running; fixes while paused are stored with `paused = true` and handed to the live
 * metrics flagged as paused, so paused movement is never counted.
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

    /** Fixes from before the start (warm-up) and repeated timestamps from an engine are dropped. */
    fun onFix(fix: LocationFix, isPrimary: Boolean) {
        if (fix.timeMillis < notBeforeMillis) return
        val previous = lastFixTime[fix.engine]
        if (previous != null && fix.timeMillis <= previous) return
        lastFixTime[fix.engine] = fix.timeMillis
        if (isPrimary) {
            metrics.onFix(fix, paused)
            routeVersion++
        }
        if (isPrimary || dualEngine) pendingPoints += fix.toRawPoint(runId, isPrimary = isPrimary, paused = paused)
    }

    fun onSteps(event: StepEvent) {
        if (event.count <= 0) return
        metrics.onSteps(event, paused)
        if (stepsAreDetectorEvents) {
            repeat(event.count) { pendingSamples += SensorSample(SensorType.STEP_DETECTOR, event.timeMillis, 1.0) }
        } else {
            cumulativeSteps += event.count
            pendingSamples += SensorSample(SensorType.STEP_COUNTER, event.timeMillis, cumulativeSteps)
        }
    }

    fun onPressure(sample: PressureSample) {
        metrics.onPressure(sample)
        pendingSamples += SensorSample(SensorType.PRESSURE, sample.timeMillis, sample.hPa)
    }

    /**
     * Writes the buffered batches. Items leave the buffer only after their write succeeded, so a
     * failed write (disk full, transient I/O) is retried by the next flush instead of being lost.
     * New items may be appended while a write is suspended; they stay queued behind the batch.
     */
    suspend fun flush(nowMillis: Long) {
        lastFlushMillis = nowMillis
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
