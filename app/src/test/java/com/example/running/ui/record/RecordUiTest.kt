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
        assertThat(gpsHint(gps(GpsQuality.POOR, allowed = false, weak = false))).isEqualTo(GpsHint.IMPROVING)
        assertThat(gpsHint(gps(GpsQuality.POOR, allowed = true, weak = true))).isEqualTo(GpsHint.WEAK)
        assertThat(gpsHint(gps(GpsQuality.GOOD, allowed = true, weak = false))).isEqualTo(GpsHint.READY)
    }

    @Test fun countdownToWeakStart() {
        assertThat(secondsUntilWeakStart(gps(GpsQuality.FAIR, false, false, waiting = 3_200))).isEqualTo(7)
        assertThat(secondsUntilWeakStart(gps(GpsQuality.FAIR, false, false, waiting = 9_999))).isEqualTo(1)
        assertThat(secondsUntilWeakStart(gps(GpsQuality.FAIR, true, true, waiting = 12_000))).isNull()
        assertThat(secondsUntilWeakStart(GpsStatus.NONE.copy(waitingMillis = 3_000))).isNull()
    }

    @Test fun permissionOutranksLocationSwitch() {
        assertThat(locationBlock(hasPermission = false, locationOn = false)).isEqualTo(LocationBlock.PERMISSION)
        assertThat(locationBlock(hasPermission = false, locationOn = true)).isEqualTo(LocationBlock.PERMISSION)
        assertThat(locationBlock(hasPermission = true, locationOn = false)).isEqualTo(LocationBlock.LOCATION_OFF)
        assertThat(locationBlock(hasPermission = true, locationOn = true)).isNull()
    }

    @Test fun startTappableWhileBlockedUnlessAFixIsInFlight() {
        assertThat(startEnabled(LocationBlock.LOCATION_OFF, GpsStatus.NONE, startPending = false)).isTrue()
        assertThat(startEnabled(LocationBlock.PERMISSION, GpsStatus.NONE, startPending = false)).isTrue()
        assertThat(startEnabled(LocationBlock.LOCATION_OFF, GpsStatus.NONE, startPending = true)).isFalse()
    }

    @Test fun startFollowsTheGateOnceUnblocked() {
        assertThat(startEnabled(null, GpsStatus.NONE, startPending = false)).isFalse()
        assertThat(startEnabled(null, gps(GpsQuality.GOOD, allowed = true, weak = false), startPending = true)).isTrue()
    }

    @Test fun pendingStartGoesThroughOnlyWhenUnblockedAndAllowed() {
        val ready = gps(GpsQuality.GOOD, allowed = true, weak = false)
        val waiting = gps(GpsQuality.POOR, allowed = false, weak = false)
        assertThat(shouldAutoStart(startPending = true, block = null, gps = ready)).isTrue()
        assertThat(shouldAutoStart(startPending = true, block = null, gps = waiting)).isFalse()
        assertThat(shouldAutoStart(startPending = true, block = null, gps = null)).isFalse()
        assertThat(shouldAutoStart(startPending = true, block = LocationBlock.LOCATION_OFF, gps = ready)).isFalse()
        assertThat(shouldAutoStart(startPending = false, block = null, gps = ready)).isFalse()
    }
}
