package com.example.running.ui.record

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.running.core.contracts.AccuracyState
import com.example.running.core.contracts.LocationAccuracyChecker
import com.example.running.core.contracts.RecordingController
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.MapRoute
import com.example.running.core.model.RecordingEvent
import com.example.running.core.model.RecordingState
import com.example.running.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Thin bridge to the process-wide [RecordingController]; the recording itself never lives in a ViewModel. */
internal class RecordViewModel(
    private val controller: RecordingController,
    val userLocation: StateFlow<GeoPoint?>,
    settings: SettingsRepository,
    private val accuracyChecker: LocationAccuracyChecker,
) : ViewModel() {
    val state: StateFlow<RecordingState> = controller.state
    val liveRoute: StateFlow<MapRoute?> = controller.liveRoute
    val events: SharedFlow<RecordingEvent> = controller.events

    /** Hidden dev mode: both engines are recorded side by side. */
    val devDualEngine: StateFlow<Boolean> = settings.settings
        .map { it.devModeUnlocked && it.devDualEngine }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _accuracyOff = MutableStateFlow(false)

    /** Google Location Accuracy is off (Google engine runs worse); F's checker, UNKNOWN counts as fine. */
    val accuracyOff: StateFlow<Boolean> = _accuracyOff.asStateFlow()

    fun refreshAccuracy() {
        viewModelScope.launch {
            _accuracyOff.value = runCatching { accuracyChecker.check() == AccuracyState.OFF }.getOrDefault(false)
        }
    }

    suspend fun accuracyResolution(): PendingIntent? = runCatching { accuracyChecker.resolution() }.getOrNull()

    /**
     * Play's one-tap "turn on location" dialog. With location off, the same HIGH_ACCURACY settings check
     * the accuracy warning uses answers RESOLUTION_REQUIRED, so F's checker hands it back. Null without
     * Play services, on a timeout, or when nothing needs resolving (the caller then opens Settings).
     */
    suspend fun turnOnLocationResolution(): PendingIntent? = runCatching {
        if (accuracyChecker.check() == AccuracyState.OFF) accuracyChecker.resolution() else null
    }.getOrNull()

    fun beginWarmUp() = controller.beginWarmUp()
    fun endWarmUp() = controller.endWarmUp()
    fun start() = controller.start()
    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun stop() = controller.stop()
    fun resumeInterrupted(runId: String) = controller.resumeInterrupted(runId)
    fun finishInterrupted(runId: String) = controller.finishInterrupted(runId)
}
