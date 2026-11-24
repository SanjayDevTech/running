package com.example.running.ui.rundetail

import app.cash.turbine.test
import com.example.running.analysis.AnalysisModule
import com.example.running.core.demo.SyntheticRuns
import com.example.running.core.model.ActivityType
import com.example.running.core.model.AppSettings
import com.example.running.testing.FakeClock
import com.example.running.testing.FakeMapOfflineManager
import com.example.running.testing.FakeRunRepository
import com.example.running.testing.FakeSettingsRepository
import com.example.running.testing.MainDispatcherRule
import com.example.running.testing.TestAppDispatchers
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class RunDetailViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = FakeClock()
    private val repo = FakeRunRepository(clock)
    private val offline = FakeMapOfflineManager()
    private val settings = FakeSettingsRepository(AppSettings())
    private val dispatchers = TestAppDispatchers(mainRule.testDispatcher)

    private suspend fun seed(id: String = "r1"): String {
        repo.importRunGraph(
            SyntheticRuns.runGraph(id, clock.nowMillis() - 3_600_000, "Asia/Kolkata", SyntheticRuns.Spec(distanceM = 3_500.0)),
        )
        return id
    }

    private fun vm(id: String) = RunDetailViewModel(
        id, repo, settings, AnalysisModule(clock).runSeriesBuilder, offline, dispatchers,
    )

    private suspend fun StateFlow<RunDetailUiState>.content(): RunDetailContent {
        var last: RunDetailUiState = value
        test {
            while (last !is RunDetailUiState.Content) last = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return (last as RunDetailUiState.Content).content
    }

    @Test fun loadsHeadlineSplitsAndRoute() = runTest(mainRule.testDispatcher) {
        val c = vm(seed()).state.content()
        assertThat(c.headline.distanceKm).isNotEmpty()
        assertThat(c.splits).isNotEmpty()
        assertThat(c.geometry.route).isNotNull()
        assertThat(c.geometry.pace.size).isAtLeast(2)
        assertThat(c.showDevCompare).isFalse()
    }

    @Test fun openingRetriesMissingOfflineAreaOnce() = runTest(mainRule.testDispatcher) {
        val id = seed()
        vm(id).state.content()
        assertThat(offline.downloads.map { it.first }).containsExactly(id)
        vm(id).state.content()   // READY now: no second download
        assertThat(offline.downloads).hasSize(1)
    }

    @Test fun unknownRunIsNotFound() = runTest(mainRule.testDispatcher) {
        vm("missing").state.test {
            var item = awaitItem()
            while (item is RunDetailUiState.Loading) item = awaitItem()
            assertThat(item).isEqualTo(RunDetailUiState.NotFound)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun renameAndSetTypeGoThroughRepository() = runTest(mainRule.testDispatcher) {
        val id = seed()
        val model = vm(id)
        model.rename("  Park loop ")
        model.setType(ActivityType.WALK)
        advanceUntilIdle()
        val run = repo.getRun(id)!!
        assertThat(run.name).isEqualTo("Park loop")
        assertThat(run.nameIsCustom).isTrue()
        assertThat(run.type).isEqualTo(ActivityType.WALK)
        model.rename("   ") // blank names are ignored
        advanceUntilIdle()
        assertThat(repo.getRun(id)!!.name).isEqualTo("Park loop")
    }

    @Test fun deleteRemovesRunThenOfflineAreaThenNavigates() = runTest(mainRule.testDispatcher) {
        val id = seed()
        var done = false
        vm(id).delete { done = true }
        advanceUntilIdle()
        assertThat(repo.getRun(id)).isNull()
        assertThat(offline.deleted).containsExactly(id)
        assertThat(done).isTrue()
    }

    @Test fun deleteDoesNotWaitForSlowOfflineRetry() = runTest(mainRule.testDispatcher) {
        val id = seed()
        offline.downloadGate = kotlinx.coroutines.CompletableDeferred() // never completes
        val model = vm(id)
        model.state.content() // opening starts the retry, which now hangs on the "network"
        assertThat(offline.downloads.map { it.first }).containsExactly(id)
        var done = false
        model.delete { done = true }
        advanceUntilIdle()
        assertThat(done).isTrue()
        assertThat(repo.getRun(id)).isNull()
        assertThat(offline.deleted).containsExactly(id)
    }

    @Test fun devCompareOnlyWhenUnlockedAndDualEngine() = runTest(mainRule.testDispatcher) {
        val id = seed()
        val g = repo.getRunGraph(id)!!
        repo.graphs.value = repo.graphs.value + (id to g.copy(run = g.run.copy(devDualEngine = true)))
        assertThat(vm(id).state.content().showDevCompare).isFalse()
        settings.state.value = AppSettings(devModeUnlocked = true)
        assertThat(vm(id).state.content().showDevCompare).isTrue()
    }
}
