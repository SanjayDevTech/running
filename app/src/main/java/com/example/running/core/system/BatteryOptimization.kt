package com.example.running.core.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimization {
    fun isIgnoring(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

    /** The one-tap system dialog. Allowed for this app: GPS recording is its core function. */
    @SuppressLint("BatteryLife")
    fun requestExemptionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
}
