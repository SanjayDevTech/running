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

}
