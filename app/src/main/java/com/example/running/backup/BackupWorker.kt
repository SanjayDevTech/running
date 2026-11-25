package com.example.running.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.running.RunningApp
import com.example.running.core.model.RecordingState

/**
 * Periodic backup job (see [BackupScheduler]). Waits while a run is being recorded or saved (retried
 * on the scheduler's backoff), skips when nothing changed since the last backup, retries a failure a
 * couple of times, then waits for the next period.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? RunningApp ?: return Result.success()
        if (deferWhile(app.container.recording.controller.state.value)) return Result.retry()
        val outcome = app.container.setup.backupManager.saveScheduledBackupIfChanged()
        return when {
            outcome.isSuccess -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    internal companion object {
        private const val MAX_ATTEMPTS = 3

        /** A backup reads every run and writes a big file: not while a run is recorded or saved. */
        fun deferWhile(state: RecordingState): Boolean =
            state is RecordingState.Recording || state is RecordingState.Saving
    }
}
