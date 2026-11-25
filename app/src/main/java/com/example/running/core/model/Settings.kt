package com.example.running.core.model

data class BackupSettings(
    val folderUri: String? = null,
    val intervalHours: Int = 0,
    val lastBackupAtMillis: Long? = null,
    /** What the last backup contained (BackupManager's change fingerprint); scheduled backups skip when unchanged. */
    val lastBackupFingerprint: String? = null,
)

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val profile: RunnerProfile = RunnerProfile(),
    val engineMode: LocationEngineMode = LocationEngineMode.AUTO,
    val devModeUnlocked: Boolean = false,
    val devDualEngine: Boolean = false,
    val backup: BackupSettings = BackupSettings(),
    val batteryPromptDismissed: Boolean = false,
)
