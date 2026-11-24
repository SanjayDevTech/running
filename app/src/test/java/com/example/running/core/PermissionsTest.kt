package com.example.running.core

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.running.core.system.Permissions
import com.google.common.truth.Truth.assertThat
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
}
