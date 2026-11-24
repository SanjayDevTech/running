package com.example.running.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// The ONLY place user-visible numbers are formatted (en, km only).

private val EN = Locale.ENGLISH
private const val EM_DASH = "–"

fun formatDistanceKm(meters: Double, decimals: Int = 2): String =
    String.format(EN, "%.${decimals}f", meters / METERS_PER_KM)

fun formatDistance(meters: Double, decimals: Int = 2): String = "${formatDistanceKm(meters, decimals)} km"

/** "5:32"; "--:--" for null, non-finite or slower than [MAX_DISPLAY_PACE_SEC_PER_KM]. */
fun formatPace(secPerKm: Double?): String {
    if (secPerKm == null || !secPerKm.isFinite() || secPerKm <= 0.0) return "--:--"
    val total = secPerKm.roundToLong()
    if (total > MAX_DISPLAY_PACE_SEC_PER_KM) return "--:--"
    return String.format(EN, "%d:%02d", total / 60, total % 60)
}

fun formatPaceWithUnit(secPerKm: Double?): String = "${formatPace(secPerKm)} /km"

/** "32:12", "1:02:03". */
fun formatDuration(millis: Long): String {
    val totalSec = (millis.coerceAtLeast(0)) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(EN, "%d:%02d:%02d", h, m, s) else String.format(EN, "%d:%02d", m, s)
}

/** "2h 19m", "45m", "38s". */
fun formatDurationShort(millis: Long): String {
    val totalSec = (millis.coerceAtLeast(0)) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${totalSec}s"
    }
}

/** "106 m", "–". */
fun formatElevation(meters: Double?): String =
    if (meters == null || !meters.isFinite()) EM_DASH else "${meters.roundToLong()} m"

/** "412", "–". */
fun formatCalories(kcal: Double?): String =
    if (kcal == null || !kcal.isFinite()) EM_DASH else kcal.roundToLong().toString()

/** "172", "–". */
fun formatCadence(spm: Double?): String =
    if (spm == null || !spm.isFinite()) EM_DASH else spm.roundToLong().toString()

/** GPS accuracy, "±5 m"; "–" when unknown. */
fun formatAccuracy(meters: Float?): String =
    if (meters == null || !meters.isFinite()) EM_DASH else "±${meters.roundToInt()} m"

/** Plain metres: one decimal below 100 m ("4.2 m"), whole metres from 100 m ("312 m"); "–" when unknown. */
fun formatMeters(meters: Double?): String = when {
    meters == null || !meters.isFinite() -> EM_DASH
    meters < 100 -> String.format(EN, "%.1f m", meters)
    else -> "${meters.roundToInt()} m"
}

/** Seconds with two decimals, "1.02 s"; "–" when unknown. */
fun formatSecondsPrecise(millis: Double?): String =
    if (millis == null || !millis.isFinite()) EM_DASH else String.format(EN, "%.2f s", millis / 1000.0)

/** Body weight: whole kilograms without a decimal ("72"), otherwise one decimal ("72.5"). */
fun formatWeightKg(kg: Double): String =
    if (kg == Math.floor(kg)) kg.toLong().toString() else String.format(EN, "%.1f", kg)

/** File size: "1.2 MB" from 1 MB, else whole kilobytes rounded up ("12 KB"). */
fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(EN, "%.1f MB", bytes / 1_000_000.0)
    else -> "${(bytes + 999) / 1000} KB"
}

/** A 0..1 share as "42 %". */
fun formatPercent(fraction: Double): String = "${(fraction * 100).roundToInt()} %"

private val RUN_DATE_TIME = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a", EN)
private val RUN_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", EN)
private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM", EN)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", EN)
private val MONTH = DateTimeFormatter.ofPattern("MMM", EN)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", EN)

private fun zoneOf(zoneId: String): ZoneId = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC"))

/** "Tue, 24 Nov 2026 · 7:02 AM". */
fun formatRunDateTime(millis: Long, zoneId: String): String =
    RUN_DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(zoneOf(zoneId)))

/** "24 Nov 2026". */
fun formatRunDate(millis: Long, zoneId: String): String =
    RUN_DATE.format(Instant.ofEpochMilli(millis).atZone(zoneOf(zoneId)))

/** "Today", "Yesterday", "Mon" (within the last week), "12 Nov". */
fun formatRelativeDay(millis: Long, zoneId: String, clock: Clock): String {
    val zone = zoneOf(zoneId)
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
    val diff = today.toEpochDay() - day.toEpochDay()
    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Yesterday"
        diff in 2..6 -> WEEKDAY.format(day)
        else -> SHORT_DAY.format(day)
    }
}

/** Chart axis / period caption for a week starting at [startMillis]: "24 Nov". */
fun formatWeekLabel(startMillis: Long, zone: ZoneId): String =
    SHORT_DAY.format(Instant.ofEpochMilli(startMillis).atZone(zone))

/** Month label: "Nov", or "Nov 2026" with [withYear]. */
fun formatMonthLabel(startMillis: Long, zone: ZoneId, withYear: Boolean = false): String =
    (if (withYear) MONTH_YEAR else MONTH).format(Instant.ofEpochMilli(startMillis).atZone(zone))
