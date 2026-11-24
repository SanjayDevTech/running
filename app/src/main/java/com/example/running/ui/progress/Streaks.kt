package com.example.running.ui.progress

import com.example.running.core.model.Run
import com.example.running.core.util.epochDay
import java.time.ZoneId

/**
 * Activity streaks. A week streak is consecutive ISO weeks (Monday start) with at least one activity;
 * a day streak is consecutive calendar days. A streak that ended in the previous period is still
 * "current" (you have until the end of this week / today to extend it).
 */
internal data class Streaks(
    val currentWeeks: Int,
    val longestWeeks: Int,
    val currentDays: Int,
    val longestDays: Int,
)

/** ISO week index: Mondays map to consecutive integers (1970-01-01 was a Thursday, epochDay 0). */
internal fun weekIndexOfEpochDay(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7L)

internal fun computeStreaks(runs: List<Run>, nowMillis: Long, zone: ZoneId): Streaks {
    val days = runs.mapTo(HashSet()) { epochDay(it.startedAtMillis, zone) }
    val weeks = days.mapTo(HashSet()) { weekIndexOfEpochDay(it) }
    val today = epochDay(nowMillis, zone)
    return Streaks(
        currentWeeks = currentStreak(weeks, weekIndexOfEpochDay(today)),
        longestWeeks = longestStreak(weeks),
        currentDays = currentStreak(days, today),
        longestDays = longestStreak(days),
    )
}

private fun currentStreak(set: Set<Long>, now: Long): Int {
    var cursor = when {
        now in set -> now
        (now - 1) in set -> now - 1
        else -> return 0
    }
    var n = 0
    while (cursor in set) { n++; cursor-- }
    return n
}

private fun longestStreak(set: Set<Long>): Int {
    var best = 0
    for (v in set) {
        if ((v - 1) in set) continue // not the start of a streak
        var n = 0
        var c = v
        while (c in set) { n++; c++ }
        if (n > best) best = n
    }
    return best
}
