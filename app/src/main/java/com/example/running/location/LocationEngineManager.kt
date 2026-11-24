package com.example.running.location

import com.example.running.core.contracts.LocationSource
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.LocationFix
import com.example.running.core.util.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Health-check timings for the engine arbiter. */
internal data class EngineConfig(
    /** The driving engine counts as down after this long without a fix. */
    val silenceMillis: Long = 8_000,
    /** The other engine counts as healthy if its last fix is at most this old. */
    val freshMillis: Long = 3_000,
    /** AUTO returns to Google after it has delivered fixes without a gap for this long. */
    val recoverMillis: Long = 30_000,
    /** Wait before re-subscribing to an engine whose updates failed. */
    val retryMillis: Long = 5_000,
    /** How often AUTO re-checks engine health between fixes. */
    val tickMillis: Long = 1_000,
)

internal sealed interface EngineEvent {
    /** A fix; [isPrimary] when it came from the engine driving the run at that moment. */
    data class Fix(val fix: LocationFix, val isPrimary: Boolean) : EngineEvent

    /** AUTO moved the run to another engine. */
    data class Switched(val from: LocationEngine, val to: LocationEngine, val fallbackActive: Boolean) : EngineEvent

    /** The engine the mode asked for is not on this device (e.g. "Google" without Play services). */
    data class Unavailable(val engine: LocationEngine) : EngineEvent
}

/**
 * Picks which engine drives the run (GeoFence's GeofenceEngine facade idea, applied to a live stream).
 * Pure and time-injected so the fallback rules are unit-tested without Android.
 *
 * - GOOGLE / BUILT_IN: fixed, never switches.
 * - AUTO: prefers [preferred]; when the driving engine is silent for [EngineConfig.silenceMillis] (or its
 *   updates failed) while the other engine has a fresh fix, the other one takes over. Once the preferred
 *   engine has delivered gap-free fixes for [EngineConfig.recoverMillis], AUTO switches back to it.
 */
internal class EngineArbiter(
    private val mode: LocationEngineMode,
    val preferred: LocationEngine,
    private val config: EngineConfig,
    startNanos: Long,
) {
    var primary: LocationEngine = preferred
        private set
    private var primarySinceNanos = startNanos
    private val lastFixNanos = mutableMapOf<LocationEngine, Long>()
    private val healthySinceNanos = mutableMapOf<LocationEngine, Long>()
    private val failed = mutableSetOf<LocationEngine>()

    /** AUTO is driven by the built-in engine although Google is the default. */
    val fallbackActive: Boolean get() = mode == LocationEngineMode.AUTO && primary == LocationEngine.PLATFORM

    fun onFix(engine: LocationEngine, nowNanos: Long) {
        val last = lastFixNanos[engine]
        if (last == null || nowNanos - last > config.freshMillis.millisToNanos()) healthySinceNanos[engine] = nowNanos
        lastFixNanos[engine] = nowNanos
        failed -= engine
    }

    fun onFailure(engine: LocationEngine) {
        failed += engine
        healthySinceNanos.remove(engine)
    }

    /** Re-checks health; returns the previous primary if this call switched engines. */
    fun evaluate(nowNanos: Long): LocationEngine? {
        if (mode != LocationEngineMode.AUTO) return null
        val other = if (primary == LocationEngine.PLAY) LocationEngine.PLATFORM else LocationEngine.PLAY
        val primaryLast = lastFixNanos[primary] ?: primarySinceNanos
        val primaryDown = primary in failed || nowNanos - primaryLast > config.silenceMillis.millisToNanos()
        if (primaryDown && isFresh(other, nowNanos)) return switchTo(other, nowNanos)
        if (primary != preferred && isFresh(preferred, nowNanos)) {
            val since = healthySinceNanos[preferred]
            if (since != null && nowNanos - since >= config.recoverMillis.millisToNanos()) return switchTo(preferred, nowNanos)
        }
        return null
    }

    private fun isFresh(engine: LocationEngine, nowNanos: Long): Boolean =
        engine !in failed && lastFixNanos[engine]?.let { nowNanos - it <= config.freshMillis.millisToNanos() } == true

    private fun switchTo(engine: LocationEngine, nowNanos: Long): LocationEngine {
        val previous = primary
        primary = engine
        primarySinceNanos = nowNanos
        return previous
    }

    private fun Long.millisToNanos(): Long = this * 1_000_000L
}

/**
 * Runs the location engines for one warm-up/recording stretch and arbitrates between them.
 * AUTO and dev dual mode run both engines (accuracy over battery); fixed modes run only theirs.
 * Without dual mode only fixes of the driving engine are emitted.
 */
internal class LocationEngineManager(
    private val sources: List<LocationSource>,
    private val clock: Clock,
    private val config: EngineConfig = EngineConfig(),
) {
    private fun source(engine: LocationEngine): LocationSource? = sources.firstOrNull { it.engine == engine }

    /** The engine a mode starts on: AUTO prefers Google when Play services are present. */
    fun preferredEngine(mode: LocationEngineMode): LocationEngine = when (mode) {
        LocationEngineMode.GOOGLE -> LocationEngine.PLAY
        LocationEngineMode.BUILT_IN -> LocationEngine.PLATFORM
        LocationEngineMode.AUTO ->
            if (source(LocationEngine.PLAY)?.isAvailable() == true) LocationEngine.PLAY else LocationEngine.PLATFORM
    }

    fun stream(mode: LocationEngineMode, dual: Boolean, intervalMillis: Long = 1_000): Flow<EngineEvent> = channelFlow {
        val preferred = preferredEngine(mode)
        val arbiter = EngineArbiter(mode, preferred, config, clock.elapsedRealtimeNanos())
        val lock = Mutex()
        if (source(preferred)?.isAvailable() != true) send(EngineEvent.Unavailable(preferred))

        val running = if (mode == LocationEngineMode.AUTO || dual) {
            sources.filter { it.isAvailable() }.map { it.engine }.ifEmpty { listOf(preferred) }
        } else {
            listOf(preferred)
        }

        suspend fun checkSwitch() {
            val previous = arbiter.evaluate(clock.elapsedRealtimeNanos()) ?: return
            send(EngineEvent.Switched(previous, arbiter.primary, arbiter.fallbackActive))
        }

        for (engine in running) {
            val src = source(engine) ?: continue
            launch {
                src.fixes(intervalMillis)
                    .retryWhen { cause, _ ->
                        if (cause is CancellationException) return@retryWhen false
                        lock.withLock {
                            arbiter.onFailure(engine)
                            checkSwitch()
                        }
                        delay(config.retryMillis)
                        true
                    }
                    .collect { fix ->
                        lock.withLock {
                            arbiter.onFix(engine, clock.elapsedRealtimeNanos())
                            checkSwitch()
                            val isPrimary = engine == arbiter.primary
                            if (isPrimary || dual) send(EngineEvent.Fix(fix, isPrimary))
                        }
                    }
            }
        }
        if (mode == LocationEngineMode.AUTO && running.size > 1) {
            launch {
                while (true) {
                    delay(config.tickMillis)
                    lock.withLock { checkSwitch() }
                }
            }
        }
    }
}
