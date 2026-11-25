package com.example.running.core

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import app.cash.turbine.test
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.running.core.system.Permissions
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PermissionsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun backgroundLocation_requestableOnlyAfterFineLocation() {
        assertThat(Permissions.hasBackgroundLocation(app)).isFalse()
        assertThat(Permissions.canRequestBackgroundLocation(app)).isFalse()

        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertThat(Permissions.canRequestBackgroundLocation(app)).isTrue()

        shadowOf(app).grantPermissions(Permissions.BACKGROUND_LOCATION)
        assertThat(Permissions.hasBackgroundLocation(app)).isTrue()
        assertThat(Permissions.canRequestBackgroundLocation(app)).isFalse()
    }

    @Test
    fun locationEnabledFlow_followsTheSystemSwitch() = runTest {
        val lm = app.getSystemService(LocationManager::class.java)
        shadowOf(lm).setLocationEnabled(false)
        Permissions.locationEnabledFlow(app).test {
            assertThat(awaitItem()).isFalse()
            shadowOf(lm).setLocationEnabled(true)
            app.sendBroadcast(Intent(LocationManager.MODE_CHANGED_ACTION))
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(awaitItem()).isTrue()
            shadowOf(lm).setLocationEnabled(false)
            app.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
