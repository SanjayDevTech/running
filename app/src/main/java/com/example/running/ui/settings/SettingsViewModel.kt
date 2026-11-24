package com.example.running.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.running.core.contracts.AccuracyState
import com.example.running.core.contracts.LocationAccuracyChecker
import com.example.running.core.model.AppSettings
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.util.Clock
import com.example.running.data.settings.SettingsRepository
import com.example.running.setup.DevModeTapCounter
import com.example.running.setup.SetupSnapshot
import com.example.running.setup.SetupStatusReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val system: SetupSnapshot = SetupSnapshot(),
    val accuracy: AccuracyState? = null,
    val recording: Boolean = false,
    /** Transient line under the version row ("3 more taps…"). */
    val devHint: DevHint? = null,
)

internal sealed interface DevHint {
    data class TapsLeft(val n: Int) : DevHint
    data object Enabled : DevHint
}

internal class SettingsViewModel(
    private val settings: SettingsRepository,
    private val checker: LocationAccuracyChecker,
    private val status: SetupStatusReader,
    recording: Flow<Boolean>,
    private val clock: Clock,
) : ViewModel() {
    private val system = MutableStateFlow(status.read())
    private val accuracy = MutableStateFlow<AccuracyState?>(null)
    private val hint = MutableStateFlow<DevHint?>(null)
    private val taps = DevModeTapCounter()

    val state: StateFlow<SettingsUiState> = combine(settings.settings, system, accuracy, recording, hint) { s, sys, acc, rec, h ->
        SettingsUiState(s, sys, acc, rec, h)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Called on every resume: permissions may have changed in system settings. */
    fun refresh() {
        system.value = status.read()
        viewModelScope.launch { accuracy.value = checker.check() }
    }

    suspend fun accuracyResolution() = checker.resolution()

    fun setEngineMode(mode: LocationEngineMode) {
        if (state.value.recording) return
        viewModelScope.launch { settings.setEngineMode(mode) }
    }

    fun setDevDualEngine(enabled: Boolean) {
        viewModelScope.launch { settings.setDevDualEngine(enabled) }
    }

    fun disableDevMode() {
        viewModelScope.launch {
            settings.setDevDualEngine(false)
            settings.setDevModeUnlocked(false)
            hint.value = null
        }
    }

    /** Tap on the version row; the 7th tap in a streak unlocks developer mode. */
    fun tapVersion() {
        if (state.value.settings.devModeUnlocked) return
        val left = taps.tap(clock.nowMillis())
        if (left == 0) {
            hint.value = DevHint.Enabled
            viewModelScope.launch { settings.setDevModeUnlocked(true) }
        } else if (left < DevModeTapCounter.REQUIRED - 2) {
            hint.value = DevHint.TapsLeft(left)
        }
    }
}
