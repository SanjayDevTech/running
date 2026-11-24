package com.example.running.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

// The ONLY place user-visible numbers are formatted (en, km only).

private val EN = Locale.ENGLISH
private const val EM_DASH = "–"

fun formatDistanceKm(meters: Double, decimals: Int = 2): String =
    String.format(EN, "%.${decimals}f", meters / METERS_PER_KM)

fun formatDistance(meters: Double, decimals: Int = 2): String = "${formatDistanceKm(meters, decimals)} km"

/** "5:32"; "--:--" for null, non-finite or >= 60:00. */
fun formatPace(secPerKm: Double?): String {
    if (secPerKm == null || !secPerKm.isFinite() || secPerKm <= 0.0) return "--:--"
    val total = secPerKm.roundToLong()
    if (total >= 3600) return "--:--"
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

private val RUN_DATE_TIME = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a", EN)
private val RUN_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", EN)
private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM", EN)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", EN)

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
