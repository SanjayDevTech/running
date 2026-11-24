package com.example.running.ui.navigation

import android.content.Context
import android.content.Intent
import com.example.running.MainActivity

object DeepLinks {
    /** Notification tap → Record tab. */
    const val ACTION_OPEN_RECORDING = "com.example.running.action.OPEN_RECORDING"

    /** Debug only (architecture.md §6.5): `adb shell am start -n com.example.running/.MainActivity --ez seed_demo true`. */
    const val EXTRA_SEED_DEMO = "seed_demo"

    fun openRecordingIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_RECORDING)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
