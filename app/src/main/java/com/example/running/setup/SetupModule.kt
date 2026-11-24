package com.example.running.setup

import android.app.PendingIntent
import com.example.running.backup.BackupManager
import com.example.running.backup.BackupScheduler
import com.example.running.core.contracts.AccuracyState
import com.example.running.core.contracts.LocationAccuracyChecker
import com.example.running.di.CoreDeps

/** CONTRACT(F). Public signature frozen (architecture.md §3.5). */
class SetupModule(core: CoreDeps) {
    val accuracyChecker: LocationAccuracyChecker = StubAccuracyChecker()
    val backupManager: BackupManager = BackupManager()
    val backupScheduler: BackupScheduler = BackupScheduler()
}

private class StubAccuracyChecker : LocationAccuracyChecker {
    override suspend fun check(): AccuracyState = AccuracyState.UNKNOWN
    override suspend fun resolution(): PendingIntent? = null
}
