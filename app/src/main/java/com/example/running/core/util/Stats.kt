package com.example.running.core.util

// Pure Kotlin: usable from analysis/ (track B).

/**
 * Percentile [p] (0..1) of an ascending list, linearly interpolated between the two nearest ranks
 * (the same definition as numpy's default). Null for an empty list.
 */
fun percentile(sorted: List<Double>, p: Double): Double? {
    if (sorted.isEmpty()) return null
    val pos = p.coerceIn(0.0, 1.0) * (sorted.size - 1)
    val i = pos.toInt()
    val frac = pos - i
    return if (i + 1 < sorted.size) sorted[i] + (sorted[i + 1] - sorted[i]) * frac else sorted[i]
}
