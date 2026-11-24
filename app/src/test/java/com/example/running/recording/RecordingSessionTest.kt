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
}
