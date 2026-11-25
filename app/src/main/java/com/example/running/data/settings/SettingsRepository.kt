package com.example.running.data.settings

import com.example.running.core.model.AppSettings
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.RunnerProfile
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** = settings.map { it.profile } */
    val profile: Flow<RunnerProfile>
    suspend fun current(): AppSettings
    suspend fun setOnboardingComplete(done: Boolean)

    /** Writes weight/age/sex/height only (learned strides are left alone). */
    suspend fun updateProfile(profile: RunnerProfile)
    suspend fun setLearnedStrides(runStrideM: Double?, walkStrideM: Double?)
    suspend fun setEngineMode(mode: LocationEngineMode)
    suspend fun setDevModeUnlocked(unlocked: Boolean)
    suspend fun setDevDualEngine(enabled: Boolean)
    suspend fun setBackupFolderUri(uri: String?)
    suspend fun setBackupIntervalHours(hours: Int)
    suspend fun setLastBackupAt(millis: Long)
    suspend fun setLastBackupFingerprint(fingerprint: String)
    suspend fun setBatteryPromptDismissed(dismissed: Boolean)
}
