package com.example.running.location

import com.example.running.core.model.GpsQuality
import com.example.running.core.model.GpsStatus
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix

/**
 * Start gate and live GPS status. Start is allowed once a GOOD fix (<= 10 m) has been seen, or after
 * [weakTimeoutMillis] of waiting with any fresh fix (then with a weak-signal warning). A fix older than
 * [staleMillis] counts as no fix.
 */
internal class GpsGate(
    private val weakTimeoutMillis: Long = 10_000,
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
    private var goodSeen = false

    /** Starts a new wait (warm-up or resume); keeps the last fix so a running engine is not re-awaited. */
    fun reset(nowNanos: Long) {
        waitStartNanos = nowNanos
        goodSeen = false
    }

    fun onEngine(engine: LocationEngine, fallbackActive: Boolean) {
        this.engine = engine
        this.fallbackActive = fallbackActive
    }

    fun onPrimaryFix(fix: LocationFix, nowNanos: Long) {
        lastFix = fix
        lastFixNanos = nowNanos
        engine = fix.engine
        if (GpsQuality.fromAccuracy(fix.horizontalAccuracyM) == GpsQuality.GOOD) goodSeen = true
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
        val startAllowed = quality != GpsQuality.NONE && (goodSeen || waitingMillis >= weakTimeoutMillis)
        return GpsStatus(
            engine = engine,
            accuracyM = fix?.horizontalAccuracyM,
            quality = quality,
            startAllowed = startAllowed,
            weakSignalWarning = startAllowed && quality != GpsQuality.GOOD,
            fallbackActive = fallbackActive,
            waitingMillis = waitingMillis,
        )
    }
}
