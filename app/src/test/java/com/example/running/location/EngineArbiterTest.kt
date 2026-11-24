package com.example.running.location

import com.example.running.core.model.LocationEngine.PLATFORM
import com.example.running.core.model.LocationEngine.PLAY
import com.example.running.core.model.LocationEngineMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EngineArbiterTest {
    private val config = EngineConfig(silenceMillis = 8_000, freshMillis = 3_000, recoverMillis = 30_000)
    private fun ms(v: Long) = v * 1_000_000L

    @Test fun autoSwitchesWhenPrimaryGoesSilentAndOtherIsFresh() {
        val a = EngineArbiter(LocationEngineMode.AUTO, PLAY, config, 0)
        a.onFix(PLAY, ms(1_000)); a.onFix(PLATFORM, ms(1_000))
        assertThat(a.evaluate(ms(8_000))).isNull()                // PLAY silent 7 s: not yet
        a.onFix(PLATFORM, ms(9_000))
        assertThat(a.evaluate(ms(9_500))).isEqualTo(PLAY)        // silent 8.5 s, PLATFORM fresh -> switch
        assertThat(a.primary).isEqualTo(PLATFORM)
        assertThat(a.fallbackActive).isTrue()
    }

    @Test fun noSwitchWhenOtherEngineIsAlsoSilent() {
        val a = EngineArbiter(LocationEngineMode.AUTO, PLAY, config, 0)
        a.onFix(PLATFORM, ms(1_000))
        assertThat(a.evaluate(ms(20_000))).isNull()
        assertThat(a.primary).isEqualTo(PLAY)
    }

    @Test fun primaryThatNeverDeliversCountsSilentFromStart() {
        val a = EngineArbiter(LocationEngineMode.AUTO, PLAY, config, 0)
        a.onFix(PLATFORM, ms(8_500))
        assertThat(a.evaluate(ms(8_600))).isEqualTo(PLAY)
    }

    @Test fun failureSwitchesImmediately() {
        val a = EngineArbiter(LocationEngineMode.AUTO, PLAY, config, 0)
        a.onFix(PLAY, ms(1_000)); a.onFix(PLATFORM, ms(1_500))
        a.onFailure(PLAY)
        assertThat(a.evaluate(ms(2_000))).isEqualTo(PLAY)
        assertThat(a.primary).isEqualTo(PLATFORM)
    }

    @Test fun returnsToPreferredAfterSustainedHealth() {
        val a = EngineArbiter(LocationEngineMode.AUTO, PLAY, config, 0)
        a.onFailure(PLAY); a.onFix(PLATFORM, ms(500))
        a.evaluate(ms(1_000))
        assertThat(a.primary).isEqualTo(PLATFORM)
        for (t in 2_000L..40_000L step 1_000) {
            a.onFix(PLAY, ms(t)); a.onFix(PLATFORM, ms(t))
            val switched = a.evaluate(ms(t))
            if (t - 2_000 < 30_000) assertThat(switched).isNull() else { assertThat(switched).isEqualTo(PLATFORM); break }
        }
        assertThat(a.primary).isEqualTo(PLAY)
        assertThat(a.fallbackActive).isFalse()
    }

    @Test fun gapResetsRecoveryClock() {
        val a = EngineArbiter(LocationEngineMode.AUTO, PLAY, config, 0)
        a.onFailure(PLAY); a.onFix(PLATFORM, ms(500)); a.evaluate(ms(1_000))
        a.onFix(PLAY, ms(2_000))
        a.onFix(PLAY, ms(20_000)) // 18 s gap: healthy-since restarts here
        a.onFix(PLATFORM, ms(40_000)); a.onFix(PLAY, ms(40_000))
        assertThat(a.evaluate(ms(40_000))).isNull()
        a.onFix(PLATFORM, ms(50_000)); a.onFix(PLAY, ms(50_000))
        assertThat(a.evaluate(ms(50_000))).isNull() // PLAY had gaps > fresh between 20 s and 50 s
    }

    @Test fun fixedModesNeverSwitch() {
        for (mode in listOf(LocationEngineMode.GOOGLE, LocationEngineMode.BUILT_IN)) {
            val preferred = if (mode == LocationEngineMode.GOOGLE) PLAY else PLATFORM
            val other = if (preferred == PLAY) PLATFORM else PLAY
            val a = EngineArbiter(mode, preferred, config, 0)
            a.onFailure(preferred); a.onFix(other, ms(20_000))
            assertThat(a.evaluate(ms(20_000))).isNull()
            assertThat(a.primary).isEqualTo(preferred)
            assertThat(a.fallbackActive).isFalse()
        }
    }
}
