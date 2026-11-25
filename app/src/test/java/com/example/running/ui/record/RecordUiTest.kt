package com.example.running.ui.record

import com.example.running.core.model.GpsQuality
import com.example.running.core.model.GpsStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecordUiTest {
    private fun gps(quality: GpsQuality, allowed: Boolean, weak: Boolean, waiting: Long = 0) =
        GpsStatus.NONE.copy(quality = quality, startAllowed = allowed, weakSignalWarning = weak, waitingMillis = waiting)

    @Test fun hintFollowsTheGate() {
        assertThat(gpsHint(GpsStatus.NONE)).isEqualTo(GpsHint.ACQUIRING)
        assertThat(gpsHint(gps(GpsQuality.POOR, allowed = true, weak = true))).isEqualTo(GpsHint.WEAK)
        assertThat(gpsHint(gps(GpsQuality.GOOD, allowed = true, weak = false))).isEqualTo(GpsHint.READY)
    }

    @Test fun permissionOutranksLocationSwitch() {
        assertThat(locationBlock(hasPermission = false, locationOn = false)).isEqualTo(LocationBlock.PERMISSION)
        assertThat(locationBlock(hasPermission = false, locationOn = true)).isEqualTo(LocationBlock.PERMISSION)
        assertThat(locationBlock(hasPermission = true, locationOn = false)).isEqualTo(LocationBlock.LOCATION_OFF)
        assertThat(locationBlock(hasPermission = true, locationOn = true)).isNull()
    }

    @Test fun startTappableWhileBlockedUnlessAFixIsInFlight() {
        assertThat(startEnabled(LocationBlock.LOCATION_OFF, startPending = false)).isTrue()
        assertThat(startEnabled(LocationBlock.PERMISSION, startPending = false)).isTrue()
        assertThat(startEnabled(LocationBlock.LOCATION_OFF, startPending = true)).isFalse()
    }

    @Test fun startNeverWaitsForGpsOnceUnblocked() {
        assertThat(startEnabled(null, startPending = false)).isTrue()
        assertThat(startEnabled(null, startPending = true)).isTrue()
    }

    @Test fun pendingStartGoesThroughOnceUnblockedAndWarmingUp() {
        val ready = gps(GpsQuality.GOOD, allowed = true, weak = false)
        val weak = gps(GpsQuality.POOR, allowed = true, weak = true)
        assertThat(shouldAutoStart(startPending = true, block = null, gps = ready)).isTrue()
        assertThat(shouldAutoStart(startPending = true, block = null, gps = weak)).isTrue()
        assertThat(shouldAutoStart(startPending = true, block = null, gps = null)).isFalse()
        assertThat(shouldAutoStart(startPending = true, block = LocationBlock.LOCATION_OFF, gps = ready)).isFalse()
        assertThat(shouldAutoStart(startPending = false, block = null, gps = ready)).isFalse()
    }
}
