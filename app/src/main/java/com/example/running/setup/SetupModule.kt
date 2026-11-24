package com.example.running.setup

import com.example.running.BuildConfig
import com.example.running.backup.BackupManager
import com.example.running.backup.BackupScheduler
import com.example.running.core.contracts.LocationAccuracyChecker
import com.example.running.di.CoreDeps

/** CONTRACT(F). Public signature frozen (architecture.md §3.5). */
class SetupModule(core: CoreDeps) {
    val accuracyChecker: LocationAccuracyChecker = PlayLocationAccuracyChecker(core.app)
    val backupManager: BackupManager = BackupManager(
        context = core.app,
        runs = core.runRepository,
        settings = core.settings,
        clock = core.clock,
        dispatchers = core.dispatchers,
        appVersion = BuildConfig.VERSION_NAME,
    )
    val backupScheduler: BackupScheduler = BackupScheduler(core.app, core.settings)
}
