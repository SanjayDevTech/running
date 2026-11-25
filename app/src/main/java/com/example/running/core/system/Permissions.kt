package com.example.running.core.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

object Permissions {
    val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    /** Request only when [hasNotifications] is false (never on API 31–32, where it is implicit). */
    @SuppressLint("InlinedApi")
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

    /**
     * Background location can only be requested once fine location is granted. Setup code reads the
     * same rule from the pure `SetupSnapshot.canRequestBackground` (unit-testable without a Context).
     */
    fun canRequestBackgroundLocation(ctx: Context): Boolean = hasFineLocation(ctx) && !hasBackgroundLocation(ctx)

    /** POST_NOTIFICATIONS is a runtime permission from API 33; on 31–32 it is implicitly granted. */
    fun hasNotifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(ctx, NOTIFICATIONS)

    fun hasActivityRecognition(ctx: Context): Boolean = granted(ctx, ACTIVITY_RECOGNITION)

    /** This app's system "App info" page (to grant a permanently denied permission). */
    fun appSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun locationSettingsIntent(): Intent =
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isLocationEnabled(ctx: Context): Boolean =
        ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled == true

    /**
     * System location on/off as it changes, starting with the current value. Listens for
     * [LocationManager.PROVIDERS_CHANGED_ACTION] and [LocationManager.MODE_CHANGED_ACTION], so a toggle
     * from the quick-settings drawer (which does not pause the activity) is seen at once.
     */
    fun locationEnabledFlow(ctx: Context): Flow<Boolean> = callbackFlow {
        val app = ctx.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(isLocationEnabled(app))
            }
        }
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        // System broadcasts still reach a not-exported receiver.
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(isLocationEnabled(app))
        awaitClose { app.unregisterReceiver(receiver) }
    }.conflate().distinctUntilChanged()
}
