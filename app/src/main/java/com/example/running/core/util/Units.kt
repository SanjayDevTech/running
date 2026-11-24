package com.example.running.core.util

// Pure Kotlin: usable from analysis/ (track B).

const val METERS_PER_KM = 1_000.0

/** Slowest pace shown as a number (59:59 /km); slower shows as "--:--". Live pace is clamped here. */
const val MAX_DISPLAY_PACE_SEC_PER_KM = 3_599.0

/** Slower paces (30 min/km) are standing or implausible and are left out of pace charts. */
const val MAX_CHART_PACE_SEC_PER_KM = 1_800.0

/** Below this a runner is treated as standing (no meaningful pace, about 55 min/km). */
const val STANDING_SPEED_MPS = 0.3

/** Seconds per km from a speed; null if speed <= 0.3 m/s (standing). */
fun paceSecPerKm(speedMps: Double): Double? =
    if (speedMps <= STANDING_SPEED_MPS) null else METERS_PER_KM / speedMps

fun speedMps(paceSecPerKm: Double): Double = if (paceSecPerKm <= 0.0) 0.0 else METERS_PER_KM / paceSecPerKm

/** Seconds per km over a stretch; null if distance < 10 m. */
fun paceSecPerKm(distanceM: Double, durationMillis: Long): Double? =
    if (distanceM < 10.0 || durationMillis <= 0) null else (durationMillis / 1000.0) / (distanceM / METERS_PER_KM)
