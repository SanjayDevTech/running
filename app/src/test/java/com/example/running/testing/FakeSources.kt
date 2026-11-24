package com.example.running.testing

import com.example.running.core.contracts.LocationSource
import com.example.running.core.contracts.PressureSource
import com.example.running.core.contracts.StepSource
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.model.PressureSample
import com.example.running.core.model.StepEvent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Replays [fixes] on virtual time: each fix is emitted when the (test) clock reaches its timeMillis,
 * measured from the first fix. If [clock] is given it is advanced in step, so code under test that
 * reads the Clock sees matching wall time.
 */
class FakeLocationSource(
    override val engine: LocationEngine,
    private val fixes: List<LocationFix>,
    private val clock: FakeClock? = null,
    var available: Boolean = true,
) : LocationSource {
    private var failAfter: Int? = null
    private var silenceAfterMillis: Long? = null

    /** Throws after emitting [n] fixes (engine failure). */
    fun failAfter(n: Int) = apply { failAfter = n }

    /** Stops emitting (but keeps the flow open) once [ms] have passed since the first fix. */
    fun silenceAfter(ms: Long) = apply { silenceAfterMillis = ms }

    override fun isAvailable(): Boolean = available

    override fun fixes(intervalMillis: Long): Flow<LocationFix> = flow {
        if (fixes.isEmpty()) awaitCancellation()
        val t0 = fixes.first().timeMillis
        var last = t0
        fixes.forEachIndexed { i, fix ->
            failAfter?.let { if (i >= it) throw IllegalStateException("FakeLocationSource($engine) failed") }
            silenceAfterMillis?.let { if (fix.timeMillis - t0 > it) awaitCancellation() }
            val wait = fix.timeMillis - last
            if (wait > 0) {
                delay(wait)
                clock?.advanceBy(wait)
            }
            last = fix.timeMillis
            emit(fix)
        }
        awaitCancellation()
    }
}

class FakeStepSource(
    private val events: List<StepEvent>,
    override val hasStepDetector: Boolean = true,
    override val hasStepCounter: Boolean = true,
) : StepSource {
    override fun steps(): Flow<StepEvent> = flow {
        if (events.isEmpty()) awaitCancellation()
        var last = events.first().timeMillis
        for (e in events) {
            delay((e.timeMillis - last).coerceAtLeast(0))
            last = e.timeMillis
            emit(e)
        }
        awaitCancellation()
    }
}

class FakePressureSource(
    private val samples: List<PressureSample>,
    override val isAvailable: Boolean = true,
) : PressureSource {
    override fun pressure(): Flow<PressureSample> = flow {
        if (samples.isEmpty()) awaitCancellation()
        var last = samples.first().timeMillis
        for (s in samples) {
            delay((s.timeMillis - last).coerceAtLeast(0))
            last = s.timeMillis
            emit(s)
        }
        awaitCancellation()
    }
}
