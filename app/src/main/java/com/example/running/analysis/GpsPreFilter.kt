package com.example.running.analysis

import com.example.running.core.util.haversineMeters

/** Why a fix was rejected (or [ACCEPTED]). */
internal enum class FixVerdict { ACCEPTED, POOR_ACCURACY, TOO_FAST, JITTER, DUPLICATE_TIME }

/**
 * Streaming GPS rejection (therunner thresholds): accuracy > 30 m, Doppler or implied speed
 * >= 15 m/s, and moves under 0.5 m from the last accepted fix are skipped. Shared by the offline
 * analyzer and live metrics so both see the same points.
 */
internal class GpsPreFilter {
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastTime = Long.MIN_VALUE
    private var lastAccuracy = 0.0
    private var speedRejects = 0

    /** Forget the anchor (after a pause: movement during a pause must not look like a jump). */
    fun reset() {
        lastTime = Long.MIN_VALUE
        speedRejects = 0
    }

    fun check(timeMillis: Long, lat: Double, lon: Double, accuracyM: Float?, speedMps: Float?): FixVerdict {
        val acc = accuracyM?.toDouble() ?: AnalysisTuning.DEFAULT_ACCURACY_M
        if (acc > AnalysisTuning.MAX_ACCURACY_M) return FixVerdict.POOR_ACCURACY
        if (speedMps != null && speedMps >= AnalysisTuning.MAX_SPEED_MPS) return FixVerdict.TOO_FAST
        if (lastTime == Long.MIN_VALUE) return accept(timeMillis, lat, lon, acc)
        val dtMillis = timeMillis - lastTime
        if (dtMillis <= 0) return FixVerdict.DUPLICATE_TIME
        val d = haversineMeters(lastLat, lastLon, lat, lon)
        // Allow for both fixes' uncertainty before calling a hop impossible.
        val implied = (d - acc - lastAccuracy).coerceAtLeast(0.0) / (dtMillis / 1000.0)
        if (implied >= AnalysisTuning.MAX_SPEED_MPS) {
            speedRejects++
            if (speedRejects <= AnalysisTuning.MAX_CONSECUTIVE_SPEED_REJECTS) return FixVerdict.TOO_FAST
        }
        if (d < AnalysisTuning.JITTER_M && dtMillis < AnalysisTuning.JITTER_MAX_SKIP_MILLIS) return FixVerdict.JITTER
        return accept(timeMillis, lat, lon, acc)
    }

    private fun accept(timeMillis: Long, lat: Double, lon: Double, acc: Double): FixVerdict {
        lastLat = lat; lastLon = lon; lastTime = timeMillis; lastAccuracy = acc; speedRejects = 0
        return FixVerdict.ACCEPTED
    }
}
