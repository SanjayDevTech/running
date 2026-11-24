package com.example.running.recording

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.running.RunningApp
import com.example.running.core.model.RecordingState
import com.example.running.core.notifications.NotificationChannels
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Location foreground service: keeps the process alive and in the foreground while a run is recorded,
 * shows the live notification and handles its pause/resume/finish actions. The recording itself lives in
 * [DefaultRecordingController] (process scope); the service mirrors its state.
 *
 * START_STICKY: after a process kill Android restarts the service with a null intent, which asks the
 * controller to recover the ACTIVE run (auto-resume with background location, else a prompt on next open).
 */
class RecordingService : LifecycleService() {

    private val module get() = (application as RunningApp).container.recording
    private var observeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false
    private val throttle = NotificationThrottle()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val controller = module.controllerImpl
        if (!goForeground()) {
            controller.onServiceStartRefused()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_STOP -> controller.stop()
            ACTION_START -> Unit
            null -> lifecycleScope.launch { if (!controller.recoverFromService()) shutDown() }
        }
        observeState()
        return START_STICKY
    }

    private fun goForeground(): Boolean {
        if (inForeground) return true
        val clock = module.clock
        val notification = module.notifications.build(NotificationModel.from(module.controllerImpl.state.value, clock.nowMillis()))
        return try {
            startForeground(NotificationChannels.ID_RECORDING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            inForeground = true
            acquireWakeLock()
            true
        } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException
            false
        } catch (e: SecurityException) { // location permission missing
            false
        }
    }

    private fun observeState() {
        if (observeJob?.isActive == true) return
        observeJob = lifecycleScope.launch {
            var wasActive = false
            module.controllerImpl.state.collect { state ->
                val active = state is RecordingState.Recording || state is RecordingState.Saving
                if (active) {
                    wasActive = true
                    val now = module.clock.nowMillis()
                    val model = NotificationModel.from(state, now)
                    if (throttle.shouldPost(model, now)) module.notifications.notifyRecording(module.notifications.build(model))
                } else if (wasActive) {
                    shutDown()
                }
            }
        }
    }

    private fun shutDown() {
        observeJob?.cancel()
        releaseWakeLock()
        if (inForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
        stopSelf()
    }

    /** Accuracy over battery: keep the CPU up so 1 Hz fixes and the ticker never stall. */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Running:recording")
            ?.apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.running.recording.START"
        const val ACTION_PAUSE = "com.example.running.recording.PAUSE"
        const val ACTION_RESUME = "com.example.running.recording.RESUME"
        const val ACTION_STOP = "com.example.running.recording.STOP"

        /** Upper bound only; released when the run stops. */
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 12 * 60 * 60 * 1_000L

        internal fun startIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).setAction(ACTION_START)

        internal fun actionIntent(context: Context, action: String): Intent =
            Intent(context, RecordingService::class.java).setAction(action)
    }
}
