package com.example.running.recording

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.running.R
import com.example.running.core.system.Permissions

/** User-visible messages the controller emits as [com.example.running.core.model.RecordingEvent.Failure]. */
internal enum class RecordingMessage { PLAY_UNAVAILABLE, SAVE_FAILED, SERVICE_REFUSED }

/** Android-side effects of the controller, behind an interface so the controller is JVM-testable. */
internal interface RecordingPlatform {
    fun hasBackgroundLocation(): Boolean

    /** Starts (or pokes) the location foreground service; false if Android refused the start. */
    fun startRecordingService(): Boolean

    /** "Run interrupted: open the app to resume or finish" on the alerts channel. */
    fun postRecoveryAlert()

    fun cancelRecoveryAlert()

    fun text(message: RecordingMessage): String
}

/** Private, non-backed-up state (architecture.md §2.5): auto-resume attempts per run, against crash loops. */
internal interface RecoveryStore {
    fun autoResumeAttempts(runId: String): Int
    fun recordAutoResume(runId: String)
    fun clear()
}

internal class AndroidRecordingPlatform(
    context: Context,
    private val notifications: RecordingNotifications,
) : RecordingPlatform {
    private val app = context.applicationContext

    override fun hasBackgroundLocation(): Boolean = Permissions.hasBackgroundLocation(app)

    override fun startRecordingService(): Boolean = try {
        ContextCompat.startForegroundService(app, RecordingService.startIntent(app))
        true
    } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException
        false
    } catch (e: SecurityException) {
        false
    }

    override fun postRecoveryAlert() = notifications.postRecoveryAlert()

    override fun cancelRecoveryAlert() = notifications.cancelRecoveryAlert()

    override fun text(message: RecordingMessage): String = app.getString(
        when (message) {
            RecordingMessage.PLAY_UNAVAILABLE -> R.string.record_error_play_unavailable
            RecordingMessage.SAVE_FAILED -> R.string.record_error_save_failed
            RecordingMessage.SERVICE_REFUSED -> R.string.record_error_service_refused
        }
    )
}

internal class SharedPrefsRecoveryStore(context: Context) : RecoveryStore {
    private val prefs by lazy { context.applicationContext.getSharedPreferences("recording_state", Context.MODE_PRIVATE) }

    override fun autoResumeAttempts(runId: String): Int =
        if (prefs.getString(KEY_RUN, null) == runId) prefs.getInt(KEY_ATTEMPTS, 0) else 0

    override fun recordAutoResume(runId: String) {
        val next = autoResumeAttempts(runId) + 1
        prefs.edit { putString(KEY_RUN, runId).putInt(KEY_ATTEMPTS, next) }
    }

    override fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_RUN = "auto_resume_run_id"
        const val KEY_ATTEMPTS = "auto_resume_attempts"
    }
}
