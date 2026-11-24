package com.example.running.ui.settings

import app.cash.turbine.test
import com.example.running.core.contracts.AccuracyState
import com.example.running.core.model.LocationEngineMode
import com.example.running.setup.SetupSnapshot
import com.example.running.setup.SetupStatusReader
import com.example.running.testing.FakeAccuracyChecker
import com.example.running.testing.FakeClock
import com.example.running.testing.FakeSettingsRepository
import com.example.running.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val settings = FakeSettingsRepository()
    private val recording = MutableStateFlow(false)
    private val clock = FakeClock()
    private val checker = FakeAccuracyChecker(AccuracyState.OFF)
    private var snapshot = SetupSnapshot(fineLocation = true)

    private fun vm() = SettingsViewModel(settings, checker, SetupStatusReader { snapshot }, recording, clock)

    @Test
    fun engineModeChangesAreSavedButIgnoredWhileRecording() = runTest {
        val vm = vm()
        vm.state.test {
            awaitItem()
            vm.setEngineMode(LocationEngineMode.GOOGLE)
            assertThat(settings.current().engineMode).isEqualTo(LocationEngineMode.GOOGLE)
            recording.value = true
            skipItems(1)
            vm.setEngineMode(LocationEngineMode.BUILT_IN)
            assertThat(settings.current().engineMode).isEqualTo(LocationEngineMode.GOOGLE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refreshReadsPermissionsAndAccuracy() = runTest {
        val vm = vm()
        vm.state.test {
            awaitItem()
            snapshot = SetupSnapshot(fineLocation = true, backgroundLocation = true, batteryExempt = true)
            vm.refresh()
            val s = expectMostRecentItem()
            assertThat(s.system.backgroundLocation).isTrue()
            assertThat(s.accuracy).isEqualTo(AccuracyState.OFF)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sevenQuickTapsUnlockDeveloperModeAndDisableClearsDualEngine() = runTest {
        val vm = vm()
        vm.state.test {
            awaitItem()
            repeat(6) { vm.tapVersion(); clock.advanceBy(200) }
            assertThat(settings.current().devModeUnlocked).isFalse()
            vm.tapVersion()
            assertThat(settings.current().devModeUnlocked).isTrue()
            vm.setDevDualEngine(true)
            vm.disableDevMode()
            assertThat(settings.current().devModeUnlocked).isFalse()
            assertThat(settings.current().devDualEngine).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun slowTapsNeverUnlock() = runTest {
        val vm = vm()
        vm.state.test {
            awaitItem()
            repeat(10) { vm.tapVersion(); clock.advanceBy(5_000) }
            assertThat(settings.current().devModeUnlocked).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
