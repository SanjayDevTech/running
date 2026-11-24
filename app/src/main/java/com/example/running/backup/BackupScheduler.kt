package com.example.running.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.running.data.settings.SettingsRepository
import java.util.concurrent.TimeUnit

/** CONTRACT(F). Public methods frozen; owns the single periodic job `recurring-backup`. */
class BackupScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** 0 (or negative) = off; otherwise one backup every [intervalHours] hours. */
    fun apply(intervalHours: Int) {
        if (intervalHours <= 0) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    suspend fun applyFromSettings() = apply(settings.current().backup.intervalHours)

    companion object {
        const val WORK_NAME = "recurring-backup"
    }
}
