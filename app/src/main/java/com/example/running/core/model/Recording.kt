package com.example.running.core.model

data class GpsStatus(
    val engine: LocationEngine?,
    val accuracyM: Float?,
    val quality: GpsQuality,
    /** Good fix, or >= ~10 s of waiting with any fix. */
    val startAllowed: Boolean,
    /** Start allowed only by the timeout. */
    val weakSignalWarning: Boolean,
    /** AUTO switched from PLAY to PLATFORM (or back). */
    val fallbackActive: Boolean,
    val waitingMillis: Long,
) {
    companion object {
        val NONE = GpsStatus(
            engine = null, accuracyM = null, quality = GpsQuality.NONE, startAllowed = false,
            weakSignalWarning = false, fallbackActive = false, waitingMillis = 0,
        )
    }
}

data class LiveStats(
    val elapsedMillis: Long,
    val movingMillis: Long,
    val distanceM: Double,
    val currentPaceSecPerKm: Double?,
    val avgPaceSecPerKm: Double?,
    val caloriesKcal: Double?,
    val steps: Int?,
    val cadenceSpm: Double?,
    val elevationGainM: Double?,
    val currentSplitIndex: Int,
    val lastSplitPaceSecPerKm: Double?,
    /** Total time in manual pauses so far (including an open pause). */
    val pausedMillis: Long = 0,
) {
    /** The live clock: elapsed minus manual pauses. Unlike [movingMillis] it keeps running at stops (no auto-pause). */
    val activeMillis: Long get() = (elapsedMillis - pausedMillis).coerceAtLeast(0)

    companion object {
        val ZERO = LiveStats(
            elapsedMillis = 0, movingMillis = 0, distanceM = 0.0, currentPaceSecPerKm = null,
            avgPaceSecPerKm = null, caloriesKcal = null, steps = null, cadenceSpm = null,
            elevationGainM = null, currentSplitIndex = 0, lastSplitPaceSecPerKm = null,
        )
    }
}

sealed interface RecordingState {
    data object Idle : RecordingState
    data class WarmingUp(val gps: GpsStatus) : RecordingState
    data class Recording(
        val runId: String,
        val startedAtMillis: Long,
        val paused: Boolean,
        val stats: LiveStats,
        val gps: GpsStatus,
    ) : RecordingState
    data class Saving(val runId: String) : RecordingState
    data class NeedsRecoveryDecision(val runId: String, val startedAtMillis: Long, val distanceM: Double) : RecordingState
}

sealed interface RecordingEvent {
    data class RunSaved(val runId: String) : RecordingEvent
    data class EngineSwitched(val from: LocationEngine, val to: LocationEngine) : RecordingEvent
    data class Failure(val message: String) : RecordingEvent
}
