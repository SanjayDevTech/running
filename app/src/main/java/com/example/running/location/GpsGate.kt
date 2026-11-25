package com.example.running.location

import com.example.running.core.model.GpsQuality
import com.example.running.core.model.GpsStatus
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix

/**
 * Live GPS status before and during a run. Start is never blocked (user decision 2026-11-25): it is
 * allowed at once, with a weak-signal warning whenever the current fix is not GOOD (<= 10 m) or there
 * is no fix yet. A fix older than [staleMillis] counts as no fix.
 */
internal class GpsGate(
    private val staleMillis: Long = 5_000,
) {
    var engine: LocationEngine? = null
        private set
    var fallbackActive: Boolean = false
        private set
    var lastFix: LocationFix? = null
        private set
    private var lastFixNanos: Long? = null
    private var waitStartNanos: Long = 0

    /** Starts a new wait (warm-up or resume); keeps the last fix so a running engine is not re-awaited. */
    fun reset(nowNanos: Long) {
        waitStartNanos = nowNanos
    }

    fun onEngine(engine: LocationEngine, fallbackActive: Boolean) {
        this.engine = engine
        this.fallbackActive = fallbackActive
    }

    fun onPrimaryFix(fix: LocationFix, nowNanos: Long) {
        lastFix = fix
        lastFixNanos = nowNanos
        engine = fix.engine
    }

    fun status(nowNanos: Long): GpsStatus {
        val ageMillis = lastFixNanos?.let { (nowNanos - it) / 1_000_000 }
        val fix = lastFix?.takeIf { ageMillis != null && ageMillis <= staleMillis }
        val quality = when {
            fix == null -> GpsQuality.NONE
            fix.horizontalAccuracyM == null -> GpsQuality.POOR
            else -> GpsQuality.fromAccuracy(fix.horizontalAccuracyM)
        }
        val waitingMillis = ((nowNanos - waitStartNanos) / 1_000_000).coerceAtLeast(0)
        return GpsStatus(
            engine = engine,
            accuracyM = fix?.horizontalAccuracyM,
            quality = quality,
            startAllowed = true,
            weakSignalWarning = quality != GpsQuality.GOOD,
            fallbackActive = fallbackActive,
            waitingMillis = waitingMillis,
        )
    }
}
