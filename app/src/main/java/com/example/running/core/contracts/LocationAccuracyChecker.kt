package com.example.running.core.contracts

import android.app.PendingIntent

enum class AccuracyState { ON, OFF, UNKNOWN }

/** Implemented by track F (setup/). */
interface LocationAccuracyChecker {
    /** Play SettingsClient, HIGH_ACCURACY request, 5 s timeout. */
    suspend fun check(): AccuracyState

    /** One-tap "turn on Google Location Accuracy". */
    suspend fun resolution(): PendingIntent?
}
