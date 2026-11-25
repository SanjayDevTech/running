package com.example.running.recording

import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.testing.FakeRunRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecordingSessionTest {

    private suspend fun session(dual: Boolean = false, notBefore: Long = T0 + 2_000): Pair<RecordingSession, FakeRunRepository> {
        val repo = FakeRunRepository()
        repo.createActiveRun("r", T0 + 2_000, "Asia/Kolkata", LocationEngineMode.AUTO, LocationEngine.PLAY, dual)
        val s = RecordingSession("r", T0 + 2_000, dual, stepsAreDetectorEvents = true, FakeLiveMetrics(T0 + 2_000), repo, notBeforeMillis = notBefore)
        return s to repo
    }

    @Test fun dropsWarmUpAndRepeatedFixes() = runTest {
        val (s, repo) = session()
        val fixes = straightFixes(5)
        fixes.forEach { s.onFix(it, isPrimary = true) }
        s.onFix(fixes.last(), isPrimary = true) // duplicate timestamp
        s.flush(T0 + 5_000)
        assertThat(repo.getRawPoints("r").map { it.timeMillis }).containsExactly(T0 + 2_000, T0 + 3_000, T0 + 4_000).inOrder()
    }

    @Test fun secondaryFixesAreStoredOnlyInDualMode() = runTest {
        val (single, repoSingle) = session(dual = false)
        val (dual, repoDual) = session(dual = true)
        val other = straightFixes(5, LocationEngine.PLATFORM)
        other.forEach { single.onFix(it, isPrimary = false); dual.onFix(it, isPrimary = false) }
        single.flush(T0 + 5_000); dual.flush(T0 + 5_000)
        assertThat(repoSingle.getRawPoints("r")).isEmpty()
        assertThat(repoDual.getRawPoints("r").map { it.isPrimary }.toSet()).containsExactly(false)
    }

    @Test fun pauseAndResumeAreIdempotentAndPersisted() = runTest {
        val (s, repo) = session()
        s.pause(T0 + 10_000)
        s.pause(T0 + 11_000)
        s.resume(T0 + 20_000)
        s.resume(T0 + 21_000)
        val pauses = repo.getRunGraph("r")!!.pauses
        assertThat(pauses).hasSize(1)
        assertThat(pauses.single().startMillis).isEqualTo(T0 + 10_000)
        assertThat(pauses.single().endMillis).isEqualTo(T0 + 20_000)
    }

    @Test fun failedWriteKeepsTheBatchForTheNextFlush() = runTest {
        val (s, repo) = session()
        straightFixes(5).forEach { s.onFix(it, isPrimary = true) }
        s.onSteps(com.example.running.core.model.StepEvent(T0 + 3_000, 2))
        repo.failingAppends = 1
        runCatching { s.flush(T0 + 5_000) }
        assertThat(repo.getRawPoints("r")).isEmpty()
        straightFixes(7).drop(5).forEach { s.onFix(it, isPrimary = true) }
        s.flush(T0 + 10_000)
        assertThat(repo.getRawPoints("r").map { it.timeMillis })
            .containsExactly(T0 + 2_000, T0 + 3_000, T0 + 4_000, T0 + 5_000, T0 + 6_000).inOrder()
        assertThat(repo.getRunGraph("r")!!.sensorSamples).hasSize(2)
    }

    // ------------------------------------------------------------------ resume gap

    private suspend fun resumed(hasCounter: Boolean = true, anchorCounter: Double? = 10_000.0): Triple<RecordingSession, FakeRunRepository, FakeLiveMetrics> {
        val repo = FakeRunRepository()
        repo.createActiveRun("r", T0 - 120_000, "Asia/Kolkata", LocationEngineMode.AUTO, LocationEngine.PLAY, false)
        val metrics = FakeLiveMetrics(T0 - 120_000)
        val anchor = ResumeGapAnchor(
            resumedAtMillis = T0, counterAtAnchor = anchorCounter, anchorMillis = T0 - 60_000,
            stepsRecordedSinceAnchor = 0.0, bootedAtMillis = T0 - 3_600_000,
        )
        val s = RecordingSession(
            "r", T0 - 120_000, false, stepsAreDetectorEvents = true, metrics, repo,
            notBeforeMillis = T0, hasStepCounter = hasCounter, resumeGap = anchor,
        )
        return Triple(s, repo, metrics)
    }

    private fun FakeRunRepository.samples(type: com.example.running.core.model.SensorType) =
        kotlinx.coroutines.runBlocking { getRunGraph("r")!!.sensorSamples.filter { it.type == type } }

    @Test fun gapWaitsForTheCounterAndComesBeforeLaterEvents() = runTest {
        val (s, repo, metrics) = resumed()
        straightFixes(2).forEach { s.onFix(it, isPrimary = true) }
        s.onSteps(com.example.running.core.model.StepEvent(T0 + 500, 1))
        assertThat(metrics.fixes).isEmpty() // held until the gap is known
        s.onCounterReading(com.example.running.core.model.StepCounterReading(T0 + 1_200, 10_171.0))
        assertThat(metrics.resumeGaps).containsExactly(T0 to 170.0) // 171 − 1 recorded after the resume
        assertThat(metrics.fixes).hasSize(2)
        assertThat(metrics.steps).hasSize(1)
        s.flush(T0 + 5_000)
        val gap = repo.samples(com.example.running.core.model.SensorType.RESUME_GAP).single()
        assertThat(gap.timeMillis).isEqualTo(T0)
        assertThat(gap.value).isEqualTo(170.0)
        // The latest raw reading is stored as the next anchor.
        assertThat(repo.samples(com.example.running.core.model.SensorType.STEP_COUNTER_BOOT).map { it.value }).containsExactly(10_171.0)
    }

    @Test fun counterResetMeansNoStepData() = runTest {
        val (s, repo, metrics) = resumed()
        s.onCounterReading(com.example.running.core.model.StepCounterReading(T0 + 1_000, 42.0))
        assertThat(metrics.resumeGaps).containsExactly(T0 to null)
        s.flush(T0 + 5_000)
        assertThat(repo.samples(com.example.running.core.model.SensorType.RESUME_GAP).single().value).isEqualTo(-1.0)
    }

    @Test fun noCounterOrNoAnchorResolvesAtOnceWithoutSteps() = runTest {
        val (_, _, noCounter) = resumed(hasCounter = false)
        assertThat(noCounter.resumeGaps).containsExactly(T0 to null)
        val (_, _, noAnchor) = resumed(anchorCounter = null)
        assertThat(noAnchor.resumeGaps).containsExactly(T0 to null)
    }

    @Test fun silentCounterTimesOutWithoutSteps() = runTest {
        val (s, _, metrics) = resumed()
        straightFixes(8).forEach { s.onFix(it, isPrimary = true) }
        assertThat(metrics.resumeGaps).containsExactly(T0 to null) // the fix at T0 + 5 s gave up waiting
        assertThat(metrics.fixes).hasSize(8)
    }

    @Test fun routeIsRepublishedOnlyWhenItChanges() = runTest {
        val (s, _) = session(notBefore = T0)
        val fixes = straightFixes(6)
        assertThat(s.routeIfChanged()).isNotNull() // first call always publishes (empty route)
        s.onFix(fixes[0], isPrimary = true)
        assertThat(s.routeIfChanged()!!.points).hasSize(1)
        assertThat(s.routeIfChanged()).isNull()
        s.pause(T0 + 1_500)
        s.onFix(fixes[2], isPrimary = true) // paused: no route point
        s.onFix(fixes[3], isPrimary = false) // secondary engine: never a route point
        assertThat(s.routeIfChanged()).isNull()
        s.resume(T0 + 3_500)
        s.onFix(fixes[4], isPrimary = true)
        assertThat(s.routeIfChanged()!!.points).hasSize(2)
    }

    @Test fun flushWritesPointsAndSamplesTogether() = runTest {
        val (s, repo) = session()
        straightFixes(5).forEach { s.onFix(it, isPrimary = true) }
        s.onSteps(com.example.running.core.model.StepEvent(T0 + 3_000, 1))
        s.flush(T0 + 5_000)
        s.flush(T0 + 10_000) // nothing new: no write
        assertThat(repo.recordedFlushes).isEqualTo(1)
        assertThat(repo.getRawPoints("r")).hasSize(3)
    }
}
