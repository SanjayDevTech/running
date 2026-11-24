package com.example.running.recording

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.graphics.toArgb
import com.example.running.R
import com.example.running.core.notifications.NotificationChannels
import com.example.running.ui.navigation.DeepLinks
import com.example.running.ui.theme.Accent

/**
 * Ongoing recording notification (RECORDING channel) and the recovery alert (RECORDING_ALERTS).
 * Public on the lock screen; on API 36+ it asks to be promoted to a Live Update (status-bar chip with
 * the distance, progress through the current kilometre). Pause/resume/finish act on [RecordingService].
 */
internal class RecordingNotifications(context: Context) {
    private val app = context.applicationContext
    private val manager: NotificationManager? get() = app.getSystemService(NotificationManager::class.java)

    fun build(model: NotificationModel): Notification {
        val title = when (model.phase) {
            NotificationModel.Phase.STARTING -> app.getString(R.string.record_notif_title_starting)
            NotificationModel.Phase.SAVING -> app.getString(R.string.record_notif_title_saving)
            NotificationModel.Phase.RUNNING -> app.getString(R.string.record_notif_title_running, model.distanceKm)
            NotificationModel.Phase.PAUSED -> app.getString(R.string.record_notif_title_paused, model.distanceKm)
        }
        val text = when (model.phase) {
            NotificationModel.Phase.RUNNING -> app.getString(R.string.record_notif_text_running, model.pace, model.currentKm)
            NotificationModel.Phase.PAUSED -> app.getString(R.string.record_notif_text_paused, model.time)
            else -> null
        }
        val builder = Notification.Builder(app, NotificationChannels.RECORDING)
            .setSmallIcon(R.drawable.ic_record_notification)
            .setColor(Accent.toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        val base = model.chronometerBaseMillis
        if (base != null) {
            builder.setWhen(base).setUsesChronometer(true).setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }
        if (model.showPause) builder.addAction(action(R.drawable.ic_record_pause, R.string.record_action_pause, RecordingService.ACTION_PAUSE))
        if (model.showResume) builder.addAction(action(R.drawable.ic_record_play, R.string.record_action_resume, RecordingService.ACTION_RESUME))
        if (model.showStop) builder.addAction(action(R.drawable.ic_record_stop, R.string.record_action_finish, RecordingService.ACTION_STOP))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) liveUpdate(builder, model)
        return builder.build()
    }

    /** Android 16 Live Update: promoted ongoing + chip text + kilometre progress. */
    private fun liveUpdate(builder: Notification.Builder, model: NotificationModel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            builder.setRequestPromotedOngoing(true)
        } else {
            // Android 16.0 reads the same extra; the setter only arrived in 16.1 (API 36.1).
            builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        }
        builder.setShortCriticalText(app.getString(R.string.record_notif_chip, model.distanceKm))
        if (model.phase == NotificationModel.Phase.RUNNING || model.phase == NotificationModel.Phase.PAUSED) {
            builder.setStyle(
                Notification.ProgressStyle()
                    .setStyledByProgress(false)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(KM).setColor(Accent.toArgb())))
                    .setProgress(model.kmProgressMeters)
            )
        }
    }

    fun postRecoveryAlert() {
        val nm = manager ?: return
        if (!nm.areNotificationsEnabled()) return
        val notification = Notification.Builder(app, NotificationChannels.RECORDING_ALERTS)
            .setSmallIcon(R.drawable.ic_record_notification)
            .setColor(Accent.toArgb())
            .setContentTitle(app.getString(R.string.record_alert_interrupted_title))
            .setContentText(app.getString(R.string.record_alert_interrupted_text))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        runCatching { nm.notify(NotificationChannels.ID_RECORDING_ALERT, notification) }
    }

    fun cancelRecoveryAlert() {
        manager?.cancel(NotificationChannels.ID_RECORDING_ALERT)
    }

    fun notifyRecording(notification: Notification) {
        runCatching { manager?.notify(NotificationChannels.ID_RECORDING, notification) }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        app, 0, DeepLinks.openRecordingIntent(app), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(icon: Int, label: Int, action: String): Notification.Action {
        val intent = PendingIntent.getService(
            app, action.hashCode(), RecordingService.actionIntent(app, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(app, icon), app.getString(label), intent).build()
    }

    private companion object {
        const val KM = 1_000
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    }
}
