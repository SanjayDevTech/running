package com.example.running.recording

import com.example.running.core.contracts.LiveMetrics
import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.PressureSample
import com.example.running.core.model.SensorType
import com.example.running.core.model.StepEvent
import com.example.running.location.toFix

/** Where an interrupted run was found. */
internal enum class RecoveryOrigin {
    /** `checkForInterruptedRun()` on a UI launch. */
    APP_OPEN,

    /** The system restarted the sticky service after killing the process. */
    SERVICE_RESTART,
}

internal enum class RecoveryDecision { AUTO_RESUME, ASK_USER }

/**
 * Crash recovery rule (architecture.md §3.1 / §8.1): resume automatically only with background location
 * (needed to restart the location FGS without the user), and at most [maxAutoResumes] times per run so a
 * run that keeps crashing the app ends up in front of the user.
 */
internal object RecoveryPolicy {
    const val MAX_AUTO_RESUMES = 3

    fun decide(hasBackgroundLocation: Boolean, autoResumeAttempts: Int, maxAutoResumes: Int = MAX_AUTO_RESUMES): RecoveryDecision =
        if (hasBackgroundLocation && autoResumeAttempts < maxAutoResumes) RecoveryDecision.AUTO_RESUME else RecoveryDecision.ASK_USER
}

/** Marks a pause that was still open when the input was loaded (load with endedAtMillis = this). */
internal const val OPEN_END_MILLIS = Long.MAX_VALUE

/**
 * Rebuilds live metrics for a run from what was persisted, so a resumed run continues its distance,
 * pace and steps. Load the input with [OPEN_END_MILLIS] so a still-open pause stays open. Returns true
 * when the run was paused at the moment it was interrupted.
 */
internal fun replayInto(metrics: LiveMetrics, input: AnalysisInput): Boolean {
    data class Item(val time: Long, val order: Int, val apply: () -> Unit)

    val items = mutableListOf<Item>()
    var openPause = false
    input.pauses.forEach { p ->
        items += Item(p.startMillis, 0) { metrics.onPauseChanged(true, p.startMillis) }
        val end = p.endMillis
        if (end == null || end == OPEN_END_MILLIS) openPause = true
        else items += Item(end, 0) { metrics.onPauseChanged(false, end) }
    }
    input.rawPoints.forEach { p -> items += Item(p.timeMillis, 1) { metrics.onFix(p.toFix(), p.paused) } }

    val pauseRanges = input.pauses.map { it.startMillis..(it.endMillis ?: OPEN_END_MILLIS) }
    fun pausedAt(t: Long) = pauseRanges.any { t in it }
    // STEP_COUNTER samples are this run's own cumulative count (RecordingSession), starting at 0.
    var lastCount = 0.0
    input.sensorSamples.forEach { s ->
        when (s.type) {
            SensorType.STEP_DETECTOR -> items += Item(s.timeMillis, 2) { metrics.onSteps(StepEvent(s.timeMillis, 1), pausedAt(s.timeMillis)) }
            SensorType.STEP_COUNTER -> {
                val prev = lastCount
                lastCount = s.value
                if (s.value > prev) {
                    val delta = (s.value - prev).toInt()
                    items += Item(s.timeMillis, 2) { metrics.onSteps(StepEvent(s.timeMillis, delta), pausedAt(s.timeMillis)) }
                }
            }
            SensorType.PRESSURE -> items += Item(s.timeMillis, 2) { metrics.onPressure(PressureSample(s.timeMillis, s.value)) }
        }
    }
    items.sortedWith(compareBy({ it.time }, { it.order })).forEach { it.apply() }
    return openPause
}

/** Time of the last thing persisted for the run (where an interruption gap starts). */
internal fun lastRecordedMillis(input: AnalysisInput): Long =
    maxOf(
        input.startedAtMillis,
        input.rawPoints.maxOfOrNull { it.timeMillis } ?: Long.MIN_VALUE,
        input.sensorSamples.maxOfOrNull { it.timeMillis } ?: Long.MIN_VALUE,
        input.pauses.maxOfOrNull { p -> p.endMillis?.takeIf { it != OPEN_END_MILLIS } ?: p.startMillis } ?: Long.MIN_VALUE,
    )
