package com.example.running.ui.onboarding

import com.example.running.core.contracts.AccuracyState
import com.example.running.setup.OnboardingStep
import com.example.running.setup.SetupSnapshot
import com.example.running.setup.SetupStatusReader
import com.example.running.testing.FakeAccuracyChecker
import com.example.running.testing.FakeSettingsRepository
import com.example.running.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private var snapshot = SetupSnapshot()
    private val settings = FakeSettingsRepository()
    private val checker = FakeAccuracyChecker(AccuracyState.ON)

    private fun vm() = OnboardingViewModel(settings, checker, SetupStatusReader { snapshot })

    @Test
    fun invalidProfileStaysOnTheStepAndShowsErrors() = runTest {
        val vm = vm()
        vm.setWeight("5")
        vm.submitProfile()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.PROFILE)
        assertThat(vm.state.value.showFormErrors).isTrue()
    }

    @Test
    fun validProfileIsSavedAndMovesToLocation() = runTest {
        val vm = vm()
        vm.setWeight("64")
        vm.setAge("29")
        vm.setHeight("171")
        vm.submitProfile()
        assertThat(settings.current().profile.weightKg).isEqualTo(64.0)
        assertThat(settings.current().profile.heightCm).isEqualTo(171)
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.LOCATION)
        assertThat(settings.current().onboardingComplete).isFalse()
    }

    @Test
    fun backgroundLocationStepAppearsOnlyAfterFineIsGranted() = runTest {
        val vm = vm()
        vm.submitProfile() // defaults 70 kg / 30 y are valid
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.LOCATION)

        snapshot = SetupSnapshot(fineLocation = true)
        vm.advance()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.BACKGROUND_LOCATION)
    }

    @Test
    fun skippingEverythingFinishesWithoutBlocking() = runTest {
        val vm = vm()
        vm.submitProfile()
        repeat(6) { vm.advance() }
        // Nothing granted: location, notifications, activity, battery skipped; accuracy ON -> done.
        assertThat(vm.state.value.done).isTrue()
        assertThat(settings.current().onboardingComplete).isTrue()
    }

    @Test
    fun accuracyOffShowsFixStepAndRecheckFinishes() = runTest {
        checker.state = AccuracyState.OFF
        snapshot = SetupSnapshot(true, true, true, true, true)
        val vm = vm()
        vm.submitProfile()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.ACCURACY)
        assertThat(vm.state.value.done).isFalse()
        checker.state = AccuracyState.ON
        vm.recheckAccuracy()
        assertThat(vm.state.value.done).isTrue()
    }

    @Test
    fun skippingBatteryRemembersTheDismissal() = runTest {
        snapshot = SetupSnapshot(true, true, true, true, batteryExempt = false)
        val vm = vm()
        vm.submitProfile()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.BATTERY)
        vm.skipBattery()
        assertThat(settings.current().batteryPromptDismissed).isTrue()
        assertThat(vm.state.value.done).isTrue()
    }
}
