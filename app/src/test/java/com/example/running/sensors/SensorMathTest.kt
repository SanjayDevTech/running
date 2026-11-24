package com.example.running.sensors

import com.example.running.testing.FakeClock
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SensorMathTest {
    @Test fun sensorTimestampsMapToWallClock() {
        val clock = FakeClock(startMillis = 1_000_000L)
        clock.advanceBy(10_000) // wall 1_010_000, elapsed 10 s
        val eventNanos = 7_500L * 1_000_000L // 2.5 s ago on the elapsed base
        assertThat(sensorEventWallMillis(eventNanos, clock)).isEqualTo(1_007_500L)
    }

    @Test fun stepCounterBecomesDeltas() {
        val d = StepCounterDeltas()
        assertThat(d.onReading(10_000f)).isNull()       // baseline (steps since boot)
        assertThat(d.onReading(10_003f)).isEqualTo(3)
        assertThat(d.onReading(10_003f)).isNull()
        assertThat(d.onReading(10_010f)).isEqualTo(7)
        assertThat(d.onReading(5f)).isNull()           // counter reset (reboot): new baseline
        assertThat(d.onReading(8f)).isEqualTo(3)
    }
}
