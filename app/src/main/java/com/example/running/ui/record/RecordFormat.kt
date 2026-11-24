package com.example.running.ui.record

import java.util.Locale
import kotlin.math.roundToInt

// SCR-A-1 stand-in: these belong in core/util/Format.kt (FOUNDATION). Remove after integration.
// Kept here, in the track's own package, because the Record and dev-compare screens need them now.

/** GPS accuracy, "±5 m"; "–" when unknown. */
internal fun formatAccuracy(meters: Float?): String =
    if (meters == null || !meters.isFinite()) "–" else "±${meters.roundToInt()} m"

/** Plain metres with one decimal below 100 m, "4.2 m", "312 m"; "–" when unknown. */
internal fun formatMeters(meters: Double?): String = when {
    meters == null || !meters.isFinite() -> "–"
    meters < 100 -> String.format(Locale.ENGLISH, "%.1f m", meters)
    else -> "${meters.roundToInt()} m"
}

/** A 0..1 share as "42 %". */
internal fun formatPercent(fraction: Double): String = "${(fraction * 100).roundToInt()} %"

/** Seconds with two decimals, "1.02 s"; "–" when unknown. */
internal fun formatSecondsPrecise(millis: Double?): String =
    if (millis == null || !millis.isFinite()) "–" else String.format(Locale.ENGLISH, "%.2f s", millis / 1000.0)
