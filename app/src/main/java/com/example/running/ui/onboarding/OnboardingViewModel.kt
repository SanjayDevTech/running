package com.example.running.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.running.core.contracts.AccuracyState
import com.example.running.core.contracts.LocationAccuracyChecker
import com.example.running.core.model.Sex
import com.example.running.data.settings.SettingsRepository
import com.example.running.setup.OnboardingFlow
import com.example.running.setup.OnboardingStep
import com.example.running.setup.ProfileForm
import com.example.running.setup.SetupSnapshot
import com.example.running.setup.SetupStatusReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.PROFILE,
    val form: ProfileForm = ProfileForm(),
    val showFormErrors: Boolean = false,
    val snapshot: SetupSnapshot = SetupSnapshot(),
    val accuracy: AccuracyState? = null,
    val checkingAccuracy: Boolean = false,
    val done: Boolean = false,
)

internal class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val checker: LocationAccuracyChecker,
    private val status: SetupStatusReader,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState(snapshot = status.read()))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Prefill from anything already saved (process death mid-onboarding, restored backup).
            val saved = settings.current()
            _state.update { it.copy(form = ProfileForm.from(saved.profile)) }
        }
    }

    fun setWeight(v: String) = _state.update { it.copy(form = it.form.copy(weight = v)) }
    fun setAge(v: String) = _state.update { it.copy(form = it.form.copy(age = v)) }
    fun setHeight(v: String) = _state.update { it.copy(form = it.form.copy(height = v)) }
    fun setSex(v: Sex) = _state.update { it.copy(form = it.form.copy(sex = v)) }

    /** Re-read permissions (after a system dialog or returning from Settings). */
    fun refresh() = _state.update { it.copy(snapshot = status.read()) }

    /** Saves the profile and moves on; stays put with errors shown when the form is invalid. */
    fun submitProfile() {
        val current = _state.value
        viewModelScope.launch {
            val profile = current.form.toProfile(settings.current().profile)
            if (profile == null) {
                _state.update { it.copy(showFormErrors = true) }
                return@launch
            }
            settings.updateProfile(profile)
            advance()
        }
    }

    /** "Skip" and "after the system answered" both land here. Never blocks onboarding. */
    fun advance() {
        viewModelScope.launch {
            val snap = status.read()
            _state.update { it.copy(snapshot = snap) }
            var accuracy = _state.value.accuracy
            val dismissed = settings.current().batteryPromptDismissed
            var next = OnboardingFlow.next(_state.value.step, snap, accuracy, dismissed)
            if (next == OnboardingStep.ACCURACY && accuracy == null) {
                _state.update { it.copy(checkingAccuracy = true) }
                accuracy = checker.check()
                _state.update { it.copy(accuracy = accuracy, checkingAccuracy = false) }
                next = OnboardingFlow.next(_state.value.step, snap, accuracy, dismissed)
            }
            if (next == null) finish() else _state.update { it.copy(step = next) }
        }
    }

    fun skipBattery() {
        viewModelScope.launch {
            settings.setBatteryPromptDismissed(true)
            advance()
        }
    }

    /** Re-checks accuracy after the user tried to fix it. */
    fun recheckAccuracy() {
        viewModelScope.launch {
            val result = checker.check()
            _state.update { it.copy(accuracy = result) }
            if (result != AccuracyState.OFF) finish()
        }
    }

    suspend fun accuracyResolution() = checker.resolution()

    private suspend fun finish() {
        settings.setOnboardingComplete(true)
        _state.update { it.copy(done = true) }
    }
}
