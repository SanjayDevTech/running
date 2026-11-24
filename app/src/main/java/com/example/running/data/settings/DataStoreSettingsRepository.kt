package com.example.running.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.running.core.model.AppSettings
import com.example.running.core.model.BackupSettings
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = SettingsKeys.FILE_NAME)

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    override val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toSettings)
        .distinctUntilChanged()

    override val profile: Flow<RunnerProfile> = settings.map { it.profile }.distinctUntilChanged()

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setOnboardingComplete(done: Boolean) = edit { it[SettingsKeys.ONBOARDING_COMPLETE] = done }

    override suspend fun updateProfile(profile: RunnerProfile) = edit {
        it[SettingsKeys.PROFILE_WEIGHT_KG] = profile.weightKg
        it[SettingsKeys.PROFILE_AGE_YEARS] = profile.ageYears
        it[SettingsKeys.PROFILE_SEX] = profile.sex.name
        if (profile.heightCm != null) it[SettingsKeys.PROFILE_HEIGHT_CM] = profile.heightCm
        else it.remove(SettingsKeys.PROFILE_HEIGHT_CM)
    }

    override suspend fun setLearnedStrides(runStrideM: Double?, walkStrideM: Double?) = edit {
        // null means "nothing learned this time": keep the previous value.
        if (runStrideM != null) it[SettingsKeys.LEARNED_RUN_STRIDE_M] = runStrideM
        if (walkStrideM != null) it[SettingsKeys.LEARNED_WALK_STRIDE_M] = walkStrideM
    }

    override suspend fun setEngineMode(mode: LocationEngineMode) = edit { it[SettingsKeys.ENGINE_MODE] = mode.name }

    override suspend fun setDevModeUnlocked(unlocked: Boolean) = edit { it[SettingsKeys.DEV_MODE_UNLOCKED] = unlocked }

    override suspend fun setDevDualEngine(enabled: Boolean) = edit { it[SettingsKeys.DEV_DUAL_ENGINE] = enabled }

    override suspend fun setBackupFolderUri(uri: String?) = edit {
        if (uri != null) it[SettingsKeys.BACKUP_FOLDER_URI] = uri else it.remove(SettingsKeys.BACKUP_FOLDER_URI)
    }

    override suspend fun setBackupIntervalHours(hours: Int) =
        edit { it[SettingsKeys.BACKUP_INTERVAL_HOURS] = hours.coerceAtLeast(0) }

    override suspend fun setLastBackupAt(millis: Long) = edit { it[SettingsKeys.LAST_BACKUP_AT_MILLIS] = millis }

    override suspend fun setBatteryPromptDismissed(dismissed: Boolean) =
        edit { it[SettingsKeys.BATTERY_PROMPT_DISMISSED] = dismissed }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    companion object {
        internal fun toSettings(p: Preferences): AppSettings {
            val defaults = RunnerProfile()
            return AppSettings(
                onboardingComplete = p[SettingsKeys.ONBOARDING_COMPLETE] ?: false,
                profile = RunnerProfile(
                    weightKg = p[SettingsKeys.PROFILE_WEIGHT_KG] ?: defaults.weightKg,
                    ageYears = p[SettingsKeys.PROFILE_AGE_YEARS] ?: defaults.ageYears,
                    sex = p[SettingsKeys.PROFILE_SEX]?.let { name -> Sex.entries.firstOrNull { it.name == name } }
                        ?: Sex.UNSPECIFIED,
                    heightCm = p[SettingsKeys.PROFILE_HEIGHT_CM],
                    learnedRunStrideM = p[SettingsKeys.LEARNED_RUN_STRIDE_M],
                    learnedWalkStrideM = p[SettingsKeys.LEARNED_WALK_STRIDE_M],
                ),
                engineMode = p[SettingsKeys.ENGINE_MODE]
                    ?.let { name -> LocationEngineMode.entries.firstOrNull { it.name == name } }
                    ?: LocationEngineMode.AUTO,
                devModeUnlocked = p[SettingsKeys.DEV_MODE_UNLOCKED] ?: false,
                devDualEngine = p[SettingsKeys.DEV_DUAL_ENGINE] ?: false,
                backup = BackupSettings(
                    folderUri = p[SettingsKeys.BACKUP_FOLDER_URI],
                    intervalHours = p[SettingsKeys.BACKUP_INTERVAL_HOURS] ?: 0,
                    lastBackupAtMillis = p[SettingsKeys.LAST_BACKUP_AT_MILLIS],
                ),
                batteryPromptDismissed = p[SettingsKeys.BATTERY_PROMPT_DISMISSED] ?: false,
            )
        }
    }
}
