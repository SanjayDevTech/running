package com.example.running.location

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.running.core.model.LocationEngine
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationMappingTest {

    private fun location(mock: Boolean) = Location("gps").apply {
        time = 1_000L
        latitude = 52.0
        longitude = 4.0
        accuracy = 4f
        speed = 0f
        speedAccuracyMetersPerSecond = 0.5f
        bearing = 90f
        isMock = mock
    }

    @Test
    fun realFix_keepsDopplerSpeed() {
        val fix = location(mock = false).toFix(LocationEngine.PLATFORM)
        assertThat(fix.speedMps).isEqualTo(0f)
        assertThat(fix.speedAccuracyMps).isEqualTo(0.5f)
    }

    @Test
    fun mockFix_dropsSpeedButKeepsPositionAndBearing() {
        val fix = location(mock = true).toFix(LocationEngine.PLATFORM)
        assertThat(fix.speedMps).isNull()
        assertThat(fix.speedAccuracyMps).isNull()
        assertThat(fix.lat).isEqualTo(52.0)
        assertThat(fix.horizontalAccuracyM).isEqualTo(4f)
        assertThat(fix.bearingDeg).isEqualTo(90f)
    }
}
