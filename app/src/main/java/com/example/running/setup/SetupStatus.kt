package com.example.running.setup

import android.content.Context
import android.os.Build
import com.example.running.core.system.BatteryOptimization
import com.example.running.core.system.Permissions

/** A point-in-time read of every permission / system setting the app cares about. */
internal data class SetupSnapshot(
    val fineLocation: Boolean = false,
    val backgroundLocation: Boolean = false,
    val notifications: Boolean = false,
    val activityRecognition: Boolean = false,
    val batteryExempt: Boolean = false,
    val locationEnabled: Boolean = true,
) {
    val canRequestBackground: Boolean get() = fineLocation && !backgroundLocation
}

internal fun interface SetupStatusReader {
    fun read(): SetupSnapshot
}

internal class AndroidSetupStatusReader(private val context: Context) : SetupStatusReader {
    override fun read() = SetupSnapshot(
        fineLocation = Permissions.hasFineLocation(context),
        backgroundLocation = Permissions.hasBackgroundLocation(context),
        // POST_NOTIFICATIONS is a runtime permission from API 33; earlier it is implicitly granted.
        notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Permissions.hasNotifications(context),
        activityRecognition = Permissions.hasActivityRecognition(context),
        batteryExempt = BatteryOptimization.isIgnoring(context),
        locationEnabled = Permissions.isLocationEnabled(context),
    )
}
