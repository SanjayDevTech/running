package com.example.running.core.contracts

import com.example.running.core.model.MapRoute
import com.example.running.core.model.RecordingEvent
import com.example.running.core.model.RecordingState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Implemented by track A (recording/). */
interface RecordingController {
    val state: StateFlow<RecordingState>
    val events: SharedFlow<RecordingEvent>
    /** Accepted points so far (for RouteMap LIVE). */
    val liveRoute: StateFlow<MapRoute?>

    /** Record screen visible and idle: start location for GPS gating. */
    fun beginWarmUp()
    fun endWarmUp()
    /** No-op unless state is WarmingUp with startAllowed. */
    fun start()
    fun pause()
    fun resume()
    /** Stop → Saving → RunSaved(runId) → Idle. */
    fun stop()
    /** On UI launch: auto-resume or NeedsRecoveryDecision. */
    suspend fun checkForInterruptedRun()
    fun resumeInterrupted(runId: String)
    fun finishInterrupted(runId: String)
}
