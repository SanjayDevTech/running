package com.example.running.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    /** IMPORTANCE_LOW, ongoing live stats (A). */
    const val RECORDING = "recording"
    /** IMPORTANCE_DEFAULT: GPS lost, engine fallback, recovery (A). */
    const val RECORDING_ALERTS = "recording_alerts"
    /** IMPORTANCE_LOW (F). */
    const val BACKUP = "backup"

    const val ID_RECORDING = 1001
    const val ID_RECORDING_ALERT = 1002
    const val ID_BACKUP = 2001

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(RECORDING, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Live stats while a run is being recorded"
                    setShowBadge(false)
                },
                NotificationChannel(RECORDING_ALERTS, "Recording alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "GPS lost, engine switched, or a run needs attention"
                },
                NotificationChannel(BACKUP, "Backup", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Scheduled backup results"
                    setShowBadge(false)
                },
            )
        )
    }
}
