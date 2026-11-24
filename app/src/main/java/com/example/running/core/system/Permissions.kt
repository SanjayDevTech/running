package com.example.running.core.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

object Permissions {
    val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    const val NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    const val ACTIVITY_RECOGNITION = Manifest.permission.ACTIVITY_RECOGNITION

    /**
     * "Allow all the time" location. Request it on its own, only after fine location is granted:
     * on API 30+ the system answers the request by opening this app's location settings page
     * (it never shows an in-app dialog). Used only to restart recording after a process kill.
     */
    const val BACKGROUND_LOCATION = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    private fun granted(ctx: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    fun hasFineLocation(ctx: Context): Boolean = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)

    /** True when location is allowed "all the time" (needed to restart the location FGS from the background). */
    fun hasBackgroundLocation(ctx: Context): Boolean = granted(ctx, BACKGROUND_LOCATION)

    /** Background location can only be requested once fine location is granted. */
    fun canRequestBackgroundLocation(ctx: Context): Boolean = hasFineLocation(ctx) && !hasBackgroundLocation(ctx)

    fun hasNotifications(ctx: Context): Boolean = granted(ctx, NOTIFICATIONS)

    fun hasActivityRecognition(ctx: Context): Boolean = granted(ctx, ACTIVITY_RECOGNITION)

    /** This app's system "App info" page (to grant a permanently denied permission). */
    fun appSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun locationSettingsIntent(): Intent =
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isLocationEnabled(ctx: Context): Boolean =
        ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
}
