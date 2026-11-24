package com.example.running.ui.record

import com.example.running.core.model.GpsQuality
import com.example.running.core.model.GpsStatus
import com.example.running.location.WEAK_START_TIMEOUT_MILLIS

/** What the pre-start GPS line says. */
internal enum class GpsHint {
    /** No fix yet. */
    ACQUIRING,

    /** A fix, but not good enough yet and the weak-signal timeout has not passed. */
    IMPROVING,

    /** Good fix: start freely. */
    READY,

    /** Start allowed only because of the timeout. */
    WEAK,
}

internal fun gpsHint(gps: GpsStatus): GpsHint = when {
    gps.quality == GpsQuality.NONE -> GpsHint.ACQUIRING
    !gps.startAllowed -> GpsHint.IMPROVING
    gps.weakSignalWarning -> GpsHint.WEAK
    else -> GpsHint.READY
}

/** Seconds until a weak-signal start unlocks (null once allowed or without any fix). */
internal fun secondsUntilWeakStart(gps: GpsStatus, timeoutMillis: Long = WEAK_START_TIMEOUT_MILLIS): Int? {
    if (gps.startAllowed || gps.quality == GpsQuality.NONE) return null
    val left = timeoutMillis - gps.waitingMillis
    return if (left <= 0) null else ((left + 999) / 1000).toInt()
}
