package com.example.running.recording

import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.PauseInterval
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.SensorSample
import com.example.running.core.model.SensorType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecoveryTest {

    private fun input(
        pauses: List<PauseInterval> = emptyList(),
        samples: List<SensorSample> = emptyList(),
        pausedFrom: Long = Long.MAX_VALUE,
    ): AnalysisInput {
        val points = straightFixes(10).map { it.toRawPoint("r", isPrimary = true, paused = it.timeMillis >= pausedFrom) }
        return AnalysisInput("r", T0, OPEN_END_MILLIS, "Asia/Kolkata", points, pauses, samples, RunnerProfile(), null)
    }

    @Test fun policyAutoResumesOnlyWithBackgroundLocationAndUnderTheCap() {
        assertThat(RecoveryPolicy.decide(hasBackgroundLocation = true, autoResumeAttempts = 0)).isEqualTo(RecoveryDecision.AUTO_RESUME)
        assertThat(RecoveryPolicy.decide(hasBackgroundLocation = true, autoResumeAttempts = 2)).isEqualTo(RecoveryDecision.AUTO_RESUME)
        assertThat(RecoveryPolicy.decide(hasBackgroundLocation = true, autoResumeAttempts = 3)).isEqualTo(RecoveryDecision.ASK_USER)
        assertThat(RecoveryPolicy.decide(hasBackgroundLocation = false, autoResumeAttempts = 0)).isEqualTo(RecoveryDecision.ASK_USER)
    }

    @Test fun replayFeedsFixesAndClosedPausesInTimeOrder() {
        val metrics = FakeLiveMetrics(T0)
        val paused = replayInto(metrics, input(pauses = listOf(PauseInterval(T0 + 3_500, T0 + 6_500))))
        assertThat(paused).isFalse()
        assertThat(metrics.fixes).hasSize(10)
        assertThat(metrics.pauseChanges).containsExactly(true to T0 + 3_500, false to T0 + 6_500).inOrder()
    }

    @Test fun openPauseIsReportedAndStaysOpen() {
        val metrics = FakeLiveMetrics(T0)
        val paused = replayInto(metrics, input(pauses = listOf(PauseInterval(T0 + 5_500, OPEN_END_MILLIS)), pausedFrom = T0 + 6_000))
        assertThat(paused).isTrue()
        assertThat(metrics.pauseChanges).containsExactly(true to T0 + 5_500)
        assertThat(metrics.fixes.filter { it.second }.map { it.first.timeMillis }).containsExactly(T0 + 6_000, T0 + 7_000, T0 + 8_000, T0 + 9_000)
    }

    @Test fun stepSamplesAreReplayedAsEvents() {
        val metrics = FakeLiveMetrics(T0)
        replayInto(
            metrics,
            input(
                samples = listOf(
                    SensorSample(SensorType.STEP_COUNTER, T0 + 1_000, 3.0),
                    SensorSample(SensorType.STEP_COUNTER, T0 + 2_000, 7.0),
                    SensorSample(SensorType.STEP_DETECTOR, T0 + 2_500, 1.0),
                    SensorSample(SensorType.PRESSURE, T0 + 3_000, 912.3),
                ),
            ),
        )
        assertThat(metrics.steps.map { it.first.count }).containsExactly(3, 4, 1).inOrder()
        assertThat(metrics.pressure.single().hPa).isEqualTo(912.3)
    }

    @Test fun lastRecordedIsTheLatestPersistedThing() {
        assertThat(lastRecordedMillis(input())).isEqualTo(T0 + 9_000)
        assertThat(lastRecordedMillis(input(samples = listOf(SensorSample(SensorType.PRESSURE, T0 + 12_000, 900.0)))))
            .isEqualTo(T0 + 12_000)
        assertThat(lastRecordedMillis(input(pauses = listOf(PauseInterval(T0 + 15_000, OPEN_END_MILLIS))))).isEqualTo(T0 + 15_000)
    }

    @Test fun resumeGapsAreReplayedBeforeFixesAtTheSameTime() {
        val metrics = FakeLiveMetrics(T0)
        replayInto(
            metrics,
            input(
                samples = listOf(
                    SensorSample(SensorType.STEP_COUNTER_BOOT, T0 + 1_000, 12_345.0),
                    SensorSample(SensorType.RESUME_GAP, T0 + 4_000, 120.0),
                    SensorSample(SensorType.RESUME_GAP, T0 + 8_000, -1.0),
                ),
            ),
        )
        assertThat(metrics.resumeGaps).containsExactly(T0 + 4_000 to 120.0, T0 + 8_000 to null).inOrder()
        assertThat(metrics.steps).isEmpty() // the boot counter is only an anchor
    }

    private fun anchorInput() = input(
        samples = listOf(
            SensorSample(SensorType.STEP_DETECTOR, T0 + 1_000, 1.0),
            SensorSample(SensorType.STEP_COUNTER_BOOT, T0 + 1_500, 10_000.0),
            SensorSample(SensorType.STEP_DETECTOR, T0 + 2_000, 1.0),
            SensorSample(SensorType.STEP_DETECTOR, T0 + 2_400, 1.0),
        ),
    )

    @Test fun anchorIsTheLastBootCounterAndCountsStepsRecordedAfterIt() {
        val a = resumeGapAnchor(anchorInput(), resumedAtMillis = T0 + 62_000, bootedAtMillis = T0 - 3_600_000)
        assertThat(a.counterAtAnchor).isEqualTo(10_000.0)
        assertThat(a.anchorMillis).isEqualTo(T0 + 1_500)
        assertThat(a.stepsRecordedSinceAnchor).isEqualTo(2.0)
        assertThat(a.unusable()).isFalse()
        assertThat(resumeGapAnchor(input(), T0 + 62_000, T0 - 3_600_000).unusable()).isTrue() // no counter stored
    }

    @Test fun gapStepsComeFromTheCounterWithRebootAndGlitchGuards() {
        val a = resumeGapAnchor(anchorInput(), resumedAtMillis = T0 + 62_000, bootedAtMillis = T0 - 3_600_000)
        // 10 000 at the anchor, 2 steps already recorded, 3 recorded after the resume: 175 in the gap.
        assertThat(resumeGapSteps(a, 10_180.0, T0 + 62_500, stepsSinceResume = 3.0)).isEqualTo(175.0)
        // Counter went backwards: it was reset (reboot).
        assertThat(resumeGapSteps(a, 150.0, T0 + 62_500, 0.0)).isNull()
        // Rebooted after the anchor (the counter restarted and may have climbed past it).
        assertThat(resumeGapSteps(a.copy(bootedAtMillis = T0 + 30_000), 10_180.0, T0 + 62_500, 0.0)).isNull()
        // 5 000 steps in a minute is not running.
        assertThat(resumeGapSteps(a, 15_000.0, T0 + 62_500, 0.0)).isNull()
        // Standing still: zero steps is valid data.
        assertThat(resumeGapSteps(a, 10_002.0, T0 + 62_500, 0.0)).isEqualTo(0.0)
    }
}
