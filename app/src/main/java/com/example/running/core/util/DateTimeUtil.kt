package com.example.running.core.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// The only place that computes day / week / month boundaries. Weeks start Monday (ISO).
// Ranges are [start, nextStart) — LongRange.last is nextStart - 1.

internal fun LocalDate.startMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()
internal fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** Monday of the ISO week containing this date. */
internal fun LocalDate.isoWeekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** ISO week index: Mondays map to consecutive integers (1970-01-01 was a Thursday, epochDay 0). */
fun isoWeekIndex(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7L)

/** 0 = Monday … 6 = Sunday. */
fun dayOfWeekIndex(millis: Long, zone: ZoneId): Int = localDate(millis, zone).dayOfWeek.value - 1

fun startOfDay(millis: Long, zone: ZoneId): Long = localDate(millis, zone).startMillis(zone)

fun startOfWeek(millis: Long, zone: ZoneId): Long =
    localDate(millis, zone).isoWeekStart().startMillis(zone)

fun startOfMonth(millis: Long, zone: ZoneId): Long = localDate(millis, zone).withDayOfMonth(1).startMillis(zone)

fun epochDay(millis: Long, zone: ZoneId): Long = localDate(millis, zone).toEpochDay()

/** [start, nextStart): `first` is the week start, `last` is one millisecond before the next week. */
fun weekRange(millis: Long, zone: ZoneId): LongRange {
    val start = localDate(millis, zone).isoWeekStart()
    return start.startMillis(zone) until start.plusWeeks(1).startMillis(zone)
}

/** [start, nextStart): `first` is the month start, `last` is one millisecond before the next month. */
fun monthRange(millis: Long, zone: ZoneId): LongRange {
    val start = localDate(millis, zone).withDayOfMonth(1)
    return start.startMillis(zone) until start.plusMonths(1).startMillis(zone)
}
