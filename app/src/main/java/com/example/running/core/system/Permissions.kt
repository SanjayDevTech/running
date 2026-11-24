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

    private fun granted(ctx: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    fun hasFineLocation(ctx: Context): Boolean = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)

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
