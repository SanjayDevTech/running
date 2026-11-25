package com.example.running.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** DataStore Preferences file "settings". Key names are persisted: never rename. */
object SettingsKeys {
    const val FILE_NAME = "settings"

    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    val PROFILE_WEIGHT_KG = doublePreferencesKey("profile_weight_kg")
    val PROFILE_AGE_YEARS = intPreferencesKey("profile_age_years")
    val PROFILE_SEX = stringPreferencesKey("profile_sex")
    val PROFILE_HEIGHT_CM = intPreferencesKey("profile_height_cm")
    val LEARNED_RUN_STRIDE_M = doublePreferencesKey("learned_run_stride_m")
    val LEARNED_WALK_STRIDE_M = doublePreferencesKey("learned_walk_stride_m")
    val ENGINE_MODE = stringPreferencesKey("engine_mode")
    val DEV_MODE_UNLOCKED = booleanPreferencesKey("dev_mode_unlocked")
    val DEV_DUAL_ENGINE = booleanPreferencesKey("dev_dual_engine")
    val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
    val BACKUP_INTERVAL_HOURS = intPreferencesKey("backup_interval_hours")
    val LAST_BACKUP_AT_MILLIS = longPreferencesKey("last_backup_at_millis")
    val LAST_BACKUP_FINGERPRINT = stringPreferencesKey("last_backup_fingerprint")
    val BATTERY_PROMPT_DISMISSED = booleanPreferencesKey("battery_prompt_dismissed")
}
