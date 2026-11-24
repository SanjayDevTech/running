package com.example.running.core.stats

import com.example.running.core.model.Run
import com.example.running.core.util.isoWeekStart
import com.example.running.core.util.localDate
import com.example.running.core.util.startMillis
import java.time.ZoneId

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

/** 7 entries, Monday..Sunday of the week starting at [weekStartMillis]. */
fun bucketByDay(runs: List<Run>, weekStartMillis: Long, zone: ZoneId): List<PeriodTotals> {
    val monday = localDate(weekStartMillis, zone)
    return (0 until 7).map { i ->
        runs.totals(monday.plusDays(i.toLong()).startMillis(zone), monday.plusDays(i + 1L).startMillis(zone))
    }
}

/** [weeks] ISO weeks, oldest first, the last one being the week containing [now]. */
fun bucketByWeek(runs: List<Run>, weeks: Int, now: Long, zone: ZoneId): List<PeriodTotals> {
    val current = localDate(now, zone).isoWeekStart()
    return (weeks - 1 downTo 0).map { back ->
        val start = current.minusWeeks(back.toLong())
        runs.totals(start.startMillis(zone), start.plusWeeks(1).startMillis(zone))
    }
}

/** [months] calendar months, oldest first, the last one being the month containing [now]. */
fun bucketByMonth(runs: List<Run>, months: Int, now: Long, zone: ZoneId): List<PeriodTotals> {
    val current = localDate(now, zone).withDayOfMonth(1)
    return (months - 1 downTo 0).map { back ->
        val start = current.minusMonths(back.toLong())
        runs.totals(start.startMillis(zone), start.plusMonths(1).startMillis(zone))
    }
}
