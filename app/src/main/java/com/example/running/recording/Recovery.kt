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
            // Before anything recorded at the same time after the resume, as live (RecordingSession).
            SensorType.RESUME_GAP -> items += Item(s.timeMillis, 0) { metrics.onResumeGap(s.timeMillis, s.value.takeIf { it >= 0.0 }) }
            SensorType.STEP_COUNTER_BOOT -> Unit // only an anchor for the next gap
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

/**
 * What a resume needs to work out the steps taken while the app was dead: the last stored boot-counter
 * reading before the interruption, the steps the run already recorded after that reading, and when the
 * device booted (a reboot after the reading restarts the counter).
 */
internal data class ResumeGapAnchor(
    val resumedAtMillis: Long,
    val counterAtAnchor: Double?,
    val anchorMillis: Long?,
    val stepsRecordedSinceAnchor: Double,
    val bootedAtMillis: Long,
)

/** Gap steps faster than this (spm) are a counter glitch, not running. */
internal const val MAX_GAP_CADENCE_SPM = 260.0

/** A resume with a step counter waits this long for its first reading before bridging without steps. */
internal const val GAP_COUNTER_WAIT_MILLIS = 5_000L

internal fun resumeGapAnchor(input: AnalysisInput, resumedAtMillis: Long, bootedAtMillis: Long): ResumeGapAnchor {
    val samples = input.sensorSamples
    val anchor = samples.filter { it.type == SensorType.STEP_COUNTER_BOOT }.maxByOrNull { it.timeMillis }
    val since = anchor?.timeMillis ?: Long.MAX_VALUE
    val detector = samples.filter { it.type == SensorType.STEP_DETECTOR && it.timeMillis > since }.sumOf { it.value }
    val gaps = samples.filter { it.type == SensorType.RESUME_GAP && it.timeMillis > since && it.value > 0.0 }.sumOf { it.value }
    val counter = samples.filter { it.type == SensorType.STEP_COUNTER }
    val counterDelta = (counter.maxOfOrNull { it.value } ?: 0.0) -
        (counter.filter { it.timeMillis <= since }.maxOfOrNull { it.value } ?: 0.0)
    return ResumeGapAnchor(
        resumedAtMillis = resumedAtMillis,
        counterAtAnchor = anchor?.value,
        anchorMillis = anchor?.timeMillis,
        stepsRecordedSinceAnchor = if (anchor == null) 0.0 else detector + gaps + counterDelta.coerceAtLeast(0.0),
        bootedAtMillis = bootedAtMillis,
    )
}

/** True when the anchor cannot give gap steps at all (no reading stored, or the device rebooted since). */
internal fun ResumeGapAnchor.unusable(): Boolean =
    counterAtAnchor == null || anchorMillis == null || bootedAtMillis > anchorMillis

/**
 * Steps taken between the anchor reading and [counterNow] that the run has not recorded, or null when
 * there is no valid step data: no anchor, a reboot since the anchor, the counter went backwards, or an
 * implausible cadence. [stepsSinceResume] are steps recorded after the resume before this reading.
 */
internal fun resumeGapSteps(anchor: ResumeGapAnchor, counterNow: Double, readingMillis: Long, stepsSinceResume: Double): Double? {
    if (anchor.unusable()) return null
    val c = anchor.counterAtAnchor!!
    if (counterNow < c) return null
    val steps = (counterNow - c - anchor.stepsRecordedSinceAnchor - stepsSinceResume).coerceAtLeast(0.0)
    val minutes = (readingMillis - anchor.anchorMillis!!) / 60_000.0
    if (minutes <= 0.0 || steps / minutes > MAX_GAP_CADENCE_SPM) return if (steps == 0.0) 0.0 else null
    return steps
}
