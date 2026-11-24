package com.example.running.recording

import com.example.running.core.model.GpsStatus
import com.example.running.core.model.LiveStats
import com.example.running.core.model.RecordingState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationModelTest {
    private val stats = LiveStats.ZERO.copy(
        elapsedMillis = 2_000_000, movingMillis = 1_932_000, distanceM = 5_023.0, currentPaceSecPerKm = 332.0,
    )

    private fun recording(paused: Boolean) = RecordingState.Recording("r", T0, paused, stats, GpsStatus.NONE)

    @Test fun runningShowsChronometerAndPauseAndFinish() {
        val m = NotificationModel.from(recording(paused = false), nowMillis = T0 + 2_000_000)
        assertThat(m.phase).isEqualTo(NotificationModel.Phase.RUNNING)
        assertThat(m.distanceKm).isEqualTo("5.02")
        assertThat(m.pace).isEqualTo("5:32")
        assertThat(m.time).isEqualTo("32:12")
        assertThat(m.chronometerBaseMillis).isEqualTo(T0 + 2_000_000 - 1_932_000)
        assertThat(m.kmProgressMeters).isEqualTo(23)
        assertThat(m.currentKm).isEqualTo(6)
        assertThat(listOf(m.showPause, m.showResume, m.showStop)).containsExactly(true, false, true).inOrder()
    }

    @Test fun pausedHasStaticTimeAndResume() {
        val m = NotificationModel.from(recording(paused = true), nowMillis = T0)
        assertThat(m.phase).isEqualTo(NotificationModel.Phase.PAUSED)
        assertThat(m.chronometerBaseMillis).isNull()
        assertThat(listOf(m.showPause, m.showResume, m.showStop)).containsExactly(false, true, true).inOrder()
    }

    @Test fun savingAndStartingHaveNoActions() {
        val saving = NotificationModel.from(RecordingState.Saving("r"), T0)
        assertThat(saving.phase).isEqualTo(NotificationModel.Phase.SAVING)
        assertThat(saving.showStop).isFalse()
        assertThat(NotificationModel.from(RecordingState.Idle, T0).phase).isEqualTo(NotificationModel.Phase.STARTING)
    }

    @Test fun throttlePostsOnVisibleChangesOrWhenStale() {
        val t = NotificationThrottle(maxAgeMillis = 5_000)
        val a = NotificationModel.from(recording(false), T0)
        assertThat(t.shouldPost(a, 0)).isTrue()
        assertThat(t.shouldPost(a.copy(chronometerBaseMillis = 1), 1_000)).isFalse()   // only the chronometer moved
        assertThat(t.shouldPost(a.copy(distanceKm = "5.03"), 2_000)).isTrue()
        assertThat(t.shouldPost(a.copy(distanceKm = "5.03"), 3_000)).isFalse()
        assertThat(t.shouldPost(a.copy(distanceKm = "5.03"), 7_001)).isTrue()
        assertThat(t.shouldPost(a.copy(distanceKm = "5.03", phase = NotificationModel.Phase.PAUSED), 7_500)).isTrue()
    }
}
