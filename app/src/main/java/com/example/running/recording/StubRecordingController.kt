package com.example.running.recording

import com.example.running.core.contracts.RecordingController
import com.example.running.core.model.MapRoute
import com.example.running.core.model.RecordingEvent
import com.example.running.core.model.RecordingState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** FOUNDATION stub: always Idle, every call a no-op. Track A replaces it. */
internal class StubRecordingController : RecordingController {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<RecordingEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<RecordingEvent> = _events.asSharedFlow()
    override val liveRoute: StateFlow<MapRoute?> = MutableStateFlow<MapRoute?>(null).asStateFlow()

    override fun beginWarmUp() = Unit
    override fun endWarmUp() = Unit
    override fun start() = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() = Unit
    override suspend fun checkForInterruptedRun() = Unit
    override fun resumeInterrupted(runId: String) = Unit
    override fun finishInterrupted(runId: String) = Unit
}
