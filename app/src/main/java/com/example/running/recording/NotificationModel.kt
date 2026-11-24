package com.example.running.recording

import com.example.running.core.model.RecordingState
import com.example.running.core.util.METERS_PER_KM
import com.example.running.core.util.formatDistanceKm
import com.example.running.core.util.formatDuration
import com.example.running.core.util.formatPace

/** What the ongoing recording notification shows; pure so the mapping is unit-tested. */
internal data class NotificationModel(
    val phase: Phase,
    /** "5.02" (km). */
    val distanceKm: String,
    /** "32:12" (time used on the Record screen). */
    val time: String,
    /** Current pace "5:32", "--:--" when unknown. */
    val pace: String,
    /** Chronometer base (epoch millis) while running; null when paused/saving (static time text). */
    val chronometerBaseMillis: Long?,
    /** Metres into the current kilometre, 0..1000, for the Live Update progress bar. */
    val kmProgressMeters: Int,
    /** 1-based kilometre currently being run. */
    val currentKm: Int,
) {
    enum class Phase { STARTING, RUNNING, PAUSED, SAVING }

    val showPause: Boolean get() = phase == Phase.RUNNING
    val showResume: Boolean get() = phase == Phase.PAUSED
    val showStop: Boolean get() = phase == Phase.RUNNING || phase == Phase.PAUSED

    companion object {
        fun from(state: RecordingState, nowMillis: Long): NotificationModel = when (state) {
            is RecordingState.Recording -> {
                val stats = state.stats
                val metersIntoKm = (stats.distanceM % METERS_PER_KM).toInt().coerceIn(0, METERS_PER_KM.toInt())
                NotificationModel(
                    phase = if (state.paused) Phase.PAUSED else Phase.RUNNING,
                    distanceKm = formatDistanceKm(stats.distanceM),
                    time = formatDuration(stats.activeMillis),
                    pace = formatPace(stats.currentPaceSecPerKm),
                    chronometerBaseMillis = if (state.paused) null else nowMillis - stats.activeMillis,
                    kmProgressMeters = metersIntoKm,
                    currentKm = (stats.distanceM / METERS_PER_KM).toInt() + 1,
                )
            }
            is RecordingState.Saving -> idle(Phase.SAVING)
            else -> idle(Phase.STARTING)
        }

        private fun idle(phase: Phase) = NotificationModel(
            phase = phase, distanceKm = formatDistanceKm(0.0), time = formatDuration(0), pace = formatPace(null),
            chronometerBaseMillis = null, kmProgressMeters = 0, currentKm = 1,
        )
    }
}

/** Notification updates are rate-limited: only when something visible changed or every [maxAgeMillis]. */
internal class NotificationThrottle(private val maxAgeMillis: Long = 5_000) {
    private var last: NotificationModel? = null
    private var lastAt = Long.MIN_VALUE

    fun shouldPost(model: NotificationModel, nowMillis: Long): Boolean {
        val prev = last
        val changed = prev == null || prev.phase != model.phase || prev.distanceKm != model.distanceKm ||
            (model.chronometerBaseMillis == null && prev.time != model.time)
        if (!changed && nowMillis - lastAt < maxAgeMillis) return false
        last = model
        lastAt = nowMillis
        return true
    }
}
