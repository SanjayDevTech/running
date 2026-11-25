package com.example.running.location

import com.example.running.core.model.GpsQuality
import com.example.running.core.model.LocationEngine
import com.example.running.recording.straightFixes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GpsGateTest {
    private fun ms(v: Long) = v * 1_000_000L
    private fun fix(acc: Float?) = straightFixes(1, accuracyM = acc).single()

    @Test fun noFixStillAllowsStartWithWarning() {
        val gate = GpsGate()
        gate.reset(0)
        val s = gate.status(ms(60_000))
        assertThat(s.quality).isEqualTo(GpsQuality.NONE)
        assertThat(s.startAllowed).isTrue()
        assertThat(s.weakSignalWarning).isTrue()
        assertThat(s.waitingMillis).isEqualTo(60_000)
    }

    @Test fun goodFixAllowsStartAtOnceWithoutWarning() {
        val gate = GpsGate()
        gate.reset(0)
        gate.onPrimaryFix(fix(6f), ms(1_000))
        val s = gate.status(ms(1_000))
        assertThat(s.quality).isEqualTo(GpsQuality.GOOD)
        assertThat(s.startAllowed).isTrue()
        assertThat(s.weakSignalWarning).isFalse()
        assertThat(s.engine).isEqualTo(LocationEngine.PLAY)
        assertThat(s.accuracyM).isEqualTo(6f)
    }

    @Test fun weakFixAllowsStartAtOnceWithWarning() {
        val gate = GpsGate()
        gate.reset(0)
        gate.onPrimaryFix(fix(25f), ms(500))
        val s = gate.status(ms(500))
        assertThat(s.quality).isEqualTo(GpsQuality.POOR)
        assertThat(s.startAllowed).isTrue()
        assertThat(s.weakSignalWarning).isTrue()
    }

    @Test fun staleFixCountsAsNoFix() {
        val gate = GpsGate(staleMillis = 5_000)
        gate.reset(0)
        gate.onPrimaryFix(fix(4f), ms(1_000))
        val s = gate.status(ms(7_000))
        assertThat(s.quality).isEqualTo(GpsQuality.NONE)
        assertThat(s.weakSignalWarning).isTrue()
        assertThat(s.accuracyM).isNull()
    }

    @Test fun fairFixAfterGoodWarns() {
        val gate = GpsGate()
        gate.reset(0)
        gate.onPrimaryFix(fix(5f), ms(1_000))
        gate.onPrimaryFix(fix(15f), ms(2_000))
        val s = gate.status(ms(2_000))
        assertThat(s.quality).isEqualTo(GpsQuality.FAIR)
        assertThat(s.startAllowed).isTrue()
        assertThat(s.weakSignalWarning).isTrue()
    }

    @Test fun resetRestartsTheWaitClock() {
        val gate = GpsGate()
        gate.reset(0)
        assertThat(gate.status(ms(12_000)).waitingMillis).isEqualTo(12_000)
        gate.reset(ms(12_000))
        assertThat(gate.status(ms(13_000)).waitingMillis).isEqualTo(1_000)
    }

    @Test fun missingAccuracyIsPoor() {
        val gate = GpsGate()
        gate.reset(0)
        gate.onPrimaryFix(fix(null), ms(500))
        assertThat(gate.status(ms(500)).quality).isEqualTo(GpsQuality.POOR)
    }
}
