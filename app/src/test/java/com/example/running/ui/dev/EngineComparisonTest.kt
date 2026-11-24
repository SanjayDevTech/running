package com.example.running.ui.dev

import com.example.running.core.model.LocationEngine
import com.example.running.recording.straightFixes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EngineComparisonTest {
    @Test fun statsPerEngine() {
        val play = straightFixes(11, LocationEngine.PLAY, accuracyM = 4f).map { it.toRawPoint("r", isPrimary = true, paused = false) }
        val platform = straightFixes(6, LocationEngine.PLATFORM, accuracyM = 9f)
            .mapIndexed { i, f -> f.copy(timeMillis = f.timeMillis * 1 + i * 1_000L) } // 2 s apart
            .map { it.toRawPoint("r", isPrimary = false, paused = false) }

        val stats = compareEngines(platform + play)

        assertThat(stats.map { it.engine }).containsExactly(LocationEngine.PLAY, LocationEngine.PLATFORM).inOrder()
        val p = stats[0]
        assertThat(p.fixes).isEqualTo(11)
        assertThat(p.distanceM).isWithin(0.5).of(30.0)
        assertThat(p.medianAccuracyM).isEqualTo(4.0)
        assertThat(p.meanIntervalMillis).isEqualTo(1_000.0)
        assertThat(p.maxGapMillis).isEqualTo(1_000L)
        assertThat(p.primaryShare).isEqualTo(1.0)
        val b = stats[1]
        assertThat(b.meanIntervalMillis).isEqualTo(2_000.0)
        assertThat(b.primaryShare).isEqualTo(0.0)
        assertThat(b.p95AccuracyM).isEqualTo(9.0)
    }

    @Test fun pausedStretchesAreNotCounted() {
        val pts = straightFixes(10).mapIndexed { i, f -> f.toRawPoint("r", isPrimary = true, paused = i in 4..6) }
        // Moving stretches 0..3 and 7..9: 3 + 2 intervals of ~3 m.
        assertThat(compareEngines(pts).single().distanceM).isWithin(0.5).of(15.0)
    }
}
