package com.example.running.testing

import com.example.running.core.model.AppSettings
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.RunnerProfile
import com.example.running.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override val profile: Flow<RunnerProfile> = state.map { it.profile }.distinctUntilChanged()

    override suspend fun current(): AppSettings = state.value
    override suspend fun setOnboardingComplete(done: Boolean) = state.update { it.copy(onboardingComplete = done) }
    override suspend fun updateProfile(profile: RunnerProfile) = state.update {
        it.copy(
            profile = it.profile.copy(
                weightKg = profile.weightKg, ageYears = profile.ageYears, sex = profile.sex, heightCm = profile.heightCm,
            )
        )
    }
    override suspend fun setLearnedStrides(runStrideM: Double?, walkStrideM: Double?) = state.update {
        it.copy(
            profile = it.profile.copy(
                learnedRunStrideM = runStrideM ?: it.profile.learnedRunStrideM,
                learnedWalkStrideM = walkStrideM ?: it.profile.learnedWalkStrideM,
            )
        )
    }
    override suspend fun setEngineMode(mode: LocationEngineMode) = state.update { it.copy(engineMode = mode) }
    override suspend fun setDevModeUnlocked(unlocked: Boolean) = state.update { it.copy(devModeUnlocked = unlocked) }
    override suspend fun setDevDualEngine(enabled: Boolean) = state.update { it.copy(devDualEngine = enabled) }
    override suspend fun setBackupFolderUri(uri: String?) = state.update { it.copy(backup = it.backup.copy(folderUri = uri)) }
    override suspend fun setBackupIntervalHours(hours: Int) =
        state.update { it.copy(backup = it.backup.copy(intervalHours = hours.coerceAtLeast(0))) }
    override suspend fun setLastBackupAt(millis: Long) =
        state.update { it.copy(backup = it.backup.copy(lastBackupAtMillis = millis)) }

    override suspend fun setLastBackupFingerprint(fingerprint: String) =
        state.update { it.copy(backup = it.backup.copy(lastBackupFingerprint = fingerprint)) }
    override suspend fun setBatteryPromptDismissed(dismissed: Boolean) =
        state.update { it.copy(batteryPromptDismissed = dismissed) }
}
