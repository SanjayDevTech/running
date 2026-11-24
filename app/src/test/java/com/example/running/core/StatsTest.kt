package com.example.running.core

import com.example.running.core.util.percentile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StatsTest {
    @Test fun percentileInterpolatesBetweenRanks() {
        val v = (1..20).map { it.toDouble() }
        assertThat(percentile(v, 0.0)).isEqualTo(1.0)
        assertThat(percentile(v, 1.0)).isEqualTo(20.0)
        assertThat(percentile(v, 0.5)).isWithin(1e-9).of(10.5)
        assertThat(percentile(v, 0.95)).isWithin(1e-9).of(19.05)
        assertThat(percentile(listOf(7.0), 0.3)).isEqualTo(7.0)
        assertThat(percentile(emptyList(), 0.5)).isNull()
    }
}
