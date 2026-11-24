package com.example.running.core.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// Weeks start Monday (ISO). Ranges are [start, nextStart) — LongRange.last is nextStart - 1.

private fun LocalDate.startMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()
private fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

fun startOfDay(millis: Long, zone: ZoneId): Long = localDate(millis, zone).startMillis(zone)

fun startOfWeek(millis: Long, zone: ZoneId): Long =
    localDate(millis, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).startMillis(zone)

fun startOfMonth(millis: Long, zone: ZoneId): Long = localDate(millis, zone).withDayOfMonth(1).startMillis(zone)

fun epochDay(millis: Long, zone: ZoneId): Long = localDate(millis, zone).toEpochDay()

/** [start, nextStart): `first` is the week start, `last` is one millisecond before the next week. */
fun weekRange(millis: Long, zone: ZoneId): LongRange {
    val start = localDate(millis, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return start.startMillis(zone) until start.plusWeeks(1).startMillis(zone)
}

/** [start, nextStart): `first` is the month start, `last` is one millisecond before the next month. */
fun monthRange(millis: Long, zone: ZoneId): LongRange {
    val start = localDate(millis, zone).withDayOfMonth(1)
    return start.startMillis(zone) until start.plusMonths(1).startMillis(zone)
}
