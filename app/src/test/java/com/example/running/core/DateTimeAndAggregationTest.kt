package com.example.running.core

import com.example.running.core.model.ActivityType
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.Run
import com.example.running.core.model.RunStats
import com.example.running.core.model.RunStatus
import com.example.running.core.stats.bucketByDay
import com.example.running.core.stats.bucketByMonth
import com.example.running.core.stats.bucketByWeek
import com.example.running.core.stats.totals
import com.example.running.core.util.epochDay
import com.example.running.core.util.monthRange
import com.example.running.core.util.startOfDay
import com.example.running.core.util.startOfMonth
import com.example.running.core.util.startOfWeek
import com.example.running.core.util.weekRange
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DateTimeAndAggregationTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun run(start: Long, km: Double, type: ActivityType = ActivityType.RUN) = Run(
        id = "r$start", name = "x", nameIsCustom = false, type = type, typeIsManual = false,
        status = RunStatus.FINISHED, startedAtMillis = start, endedAtMillis = start + 1_800_000, zoneId = zone.id,
        engineMode = LocationEngineMode.AUTO, startEngine = LocationEngine.PLAY, engineFallback = false,
        devDualEngine = false,
        stats = RunStats(distanceM = km * 1000, elapsedMillis = 1_900_000, movingMillis = 1_800_000, elevationGainM = 10.0, caloriesKcal = 300.0),
        analysisVersion = 1, createdAtMillis = start, updatedAtMillis = start,
    )

    @Test fun boundaries() {
        val wed = at(2026, 11, 25, 15) // Wednesday
        assertThat(startOfDay(wed, zone)).isEqualTo(at(2026, 11, 25))
        assertThat(startOfWeek(wed, zone)).isEqualTo(at(2026, 11, 23))
        assertThat(startOfWeek(at(2026, 11, 23), zone)).isEqualTo(at(2026, 11, 23))
        assertThat(startOfWeek(at(2026, 11, 29, 23), zone)).isEqualTo(at(2026, 11, 23))
        assertThat(startOfMonth(wed, zone)).isEqualTo(at(2026, 11, 1))
        assertThat(epochDay(wed, zone)).isEqualTo(LocalDate.of(2026, 11, 25).toEpochDay())
        val w = weekRange(wed, zone)
        assertThat(w.first).isEqualTo(at(2026, 11, 23))
        assertThat(w.last + 1).isEqualTo(at(2026, 11, 30))
        val m = monthRange(wed, zone)
        assertThat(m.first).isEqualTo(at(2026, 11, 1))
        assertThat(m.last + 1).isEqualTo(at(2026, 12, 1))
    }

    @Test fun totalsAndBuckets() {
        val runs = listOf(
            run(at(2026, 11, 23, 7), 5.0),          // Mon this week
            run(at(2026, 11, 25, 7), 10.0),         // Wed this week
            run(at(2026, 11, 25, 18), 3.0, ActivityType.WALK),
            run(at(2026, 11, 16, 7), 8.0),          // last week
            run(at(2026, 10, 5, 7), 21.0),          // October
        )
        val now = at(2026, 11, 26, 12)
        val week = runs.totals(at(2026, 11, 23), at(2026, 11, 30))
        assertThat(week.count).isEqualTo(3)
        assertThat(week.distanceM).isWithin(1e-6).of(18_000.0)
        assertThat(week.movingMillis).isEqualTo(3 * 1_800_000L)
        assertThat(week.elevationGainM).isWithin(1e-6).of(30.0)

        val days = bucketByDay(runs, at(2026, 11, 23), zone)
        assertThat(days).hasSize(7)
        assertThat(days.map { it.count }).containsExactly(1, 0, 2, 0, 0, 0, 0).inOrder()

        val weeks = bucketByWeek(runs, 3, now, zone)
        assertThat(weeks.map { it.count }).containsExactly(0, 1, 3).inOrder()
        assertThat(weeks.last().startMillis).isEqualTo(at(2026, 11, 23))

        val months = bucketByMonth(runs, 2, now, zone)
        assertThat(months.map { it.distanceM }).containsExactly(21_000.0, 26_000.0).inOrder()
        assertThat(months.first().startMillis).isEqualTo(at(2026, 10, 1))
    }
}
