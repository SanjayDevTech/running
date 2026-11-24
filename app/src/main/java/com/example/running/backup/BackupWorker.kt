package com.example.running.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.running.RunningApp

/** Periodic backup job (see [BackupScheduler]). Retries a couple of times, then waits for the next period. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? RunningApp ?: return Result.success()
        val outcome = app.container.setup.backupManager.saveScheduledBackup()
        return when {
            outcome.isSuccess -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
