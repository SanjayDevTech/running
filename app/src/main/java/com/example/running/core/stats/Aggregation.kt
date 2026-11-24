package com.example.running.core.stats

import com.example.running.core.model.Run
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// Shared by D (Activities "This week") and E (Progress charts). Runs are bucketed by startedAtMillis.

data class PeriodTotals(
    val startMillis: Long,
    val endMillis: Long,
    val count: Int,
    val distanceM: Double,
    val movingMillis: Long,
    val elapsedMillis: Long,
    val elevationGainM: Double,
    val caloriesKcal: Double,
)

/** Totals of runs whose start falls in [startMillis, endMillis). */
fun List<Run>.totals(startMillis: Long, endMillis: Long): PeriodTotals {
    var count = 0
    var distance = 0.0
    var moving = 0L
    var elapsed = 0L
    var gain = 0.0
    var kcal = 0.0
    for (r in this) {
        if (r.startedAtMillis < startMillis || r.startedAtMillis >= endMillis) continue
        count++
        distance += r.stats.distanceM
        moving += r.stats.movingMillis
        elapsed += r.stats.elapsedMillis
        gain += r.stats.elevationGainM ?: 0.0
        kcal += r.stats.caloriesKcal ?: 0.0
    }
    return PeriodTotals(startMillis, endMillis, count, distance, moving, elapsed, gain, kcal)
}

private fun LocalDate.millis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

/** 7 entries, Monday..Sunday of the week starting at [weekStartMillis]. */
fun bucketByDay(runs: List<Run>, weekStartMillis: Long, zone: ZoneId): List<PeriodTotals> {
    val monday = Instant.ofEpochMilli(weekStartMillis).atZone(zone).toLocalDate()
    return (0 until 7).map { i ->
        runs.totals(monday.plusDays(i.toLong()).millis(zone), monday.plusDays(i + 1L).millis(zone))
    }
}

/** [weeks] ISO weeks, oldest first, the last one being the week containing [now]. */
fun bucketByWeek(runs: List<Run>, weeks: Int, now: Long, zone: ZoneId): List<PeriodTotals> {
    val current = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (weeks - 1 downTo 0).map { back ->
        val start = current.minusWeeks(back.toLong())
        runs.totals(start.millis(zone), start.plusWeeks(1).millis(zone))
    }
}

/** [months] calendar months, oldest first, the last one being the month containing [now]. */
fun bucketByMonth(runs: List<Run>, months: Int, now: Long, zone: ZoneId): List<PeriodTotals> {
    val current = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().withDayOfMonth(1)
    return (months - 1 downTo 0).map { back ->
        val start = current.minusMonths(back.toLong())
        runs.totals(start.millis(zone), start.plusMonths(1).millis(zone))
    }
}
