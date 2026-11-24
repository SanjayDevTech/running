package com.example.running.ui.progress

import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.ActivityType
import com.example.running.core.model.BestEffort
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.EffortRecord
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.PersonalBests
import com.example.running.core.model.Run
import com.example.running.core.model.RunRecord
import com.example.running.core.model.RunStats
import com.example.running.core.model.RunStatus
import com.example.running.core.util.isoWeekIndex
import com.example.running.testing.FakeClock
import com.example.running.testing.FakeRunRepository
import com.example.running.testing.MainDispatcherRule
import com.example.running.testing.TestAppDispatchers
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressLogicTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 7) =
        ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun run(start: Long, km: Double, type: ActivityType = ActivityType.RUN) = Run(
        id = "r$start", name = "Run", nameIsCustom = false, type = type, typeIsManual = false,
        status = RunStatus.FINISHED, startedAtMillis = start, endedAtMillis = start + 1_800_000, zoneId = zone.id,
        engineMode = LocationEngineMode.AUTO, startEngine = LocationEngine.PLAY, engineFallback = false,
        devDualEngine = false,
        stats = RunStats(distanceM = km * 1000, elapsedMillis = 1_900_000, movingMillis = 1_800_000, elevationGainM = 10.0),
        analysisVersion = 1, createdAtMillis = start, updatedAtMillis = start,
    )

    private val now = at(2026, 11, 25, 15) // Wednesday

    @Test fun weekStreaks() {
        val runs = listOf(
            run(at(2026, 11, 24), 5.0), // this week
            run(at(2026, 11, 17), 5.0), // last week
            run(at(2026, 11, 10), 5.0), // week before
            run(at(2026, 10, 20), 5.0), // gap, then 2 weeks
            run(at(2026, 10, 13), 5.0),
        )
        val s = computeStreaks(runs, now, zone)
        assertThat(s.currentWeeks).isEqualTo(3)
        assertThat(s.longestWeeks).isEqualTo(3)
    }

    @Test fun currentStreakSurvivesUntilPeriodEnds() {
        val runs = listOf(run(at(2026, 11, 24), 5.0), run(at(2026, 11, 23), 5.0)) // Mon, Tue
        // today is Wed with no run yet: day streak of 2 still current
        assertThat(computeStreaks(runs, now, zone).currentDays).isEqualTo(2)
        // Friday: broken
        assertThat(computeStreaks(runs, at(2026, 11, 27), zone).currentDays).isEqualTo(0)
        // week streak: previous week counts as current, two weeks ago does not
        val old = listOf(run(at(2026, 11, 16), 5.0))
        assertThat(computeStreaks(old, now, zone).currentWeeks).isEqualTo(1)
        assertThat(computeStreaks(old, at(2026, 12, 2), zone).currentWeeks).isEqualTo(0)
    }

    @Test fun longestDayStreakAndEmpty() {
        val runs = (1..4).map { run(at(2026, 10, it), 3.0) } + run(at(2026, 10, 10), 3.0)
        assertThat(computeStreaks(runs, now, zone).longestDays).isEqualTo(4)
        val none = computeStreaks(emptyList(), now, zone)
        assertThat(none).isEqualTo(Streaks(0, 0, 0, 0))
    }

    @Test fun weekIndexAcrossSundayMonday() {
        // Sun 2026-11-22 and Mon 2026-11-23 are in different weeks; Mon..Sun the same
        val sun = java.time.LocalDate.of(2026, 11, 22).toEpochDay()
        assertThat(isoWeekIndex(sun + 1)).isEqualTo(isoWeekIndex(sun) + 1)
        assertThat(isoWeekIndex(sun + 7)).isEqualTo(isoWeekIndex(sun + 1))
    }

    @Test fun stateBucketsAndSelection() {
        val runs = listOf(run(at(2026, 11, 24), 5.0), run(at(2026, 11, 25), 10.0), run(at(2026, 11, 17), 8.0))
        val s = buildProgressState(runs, ActivityFilter.ALL, ProgressPeriod.WEEKLY, ProgressMetric.DISTANCE, null, now, zone)
        assertThat(s.bars).hasSize(12)
        assertThat(s.selectedIndex).isEqualTo(11)
        assertThat(s.selected.distanceM).isEqualTo(15_000.0)
        assertThat(s.bars.last().value).isEqualTo(15_000.0)
        assertThat(s.bars[10].value).isEqualTo(8_000.0)
        assertThat(s.isCurrentSelected).isTrue()
        assertThat(s.allTime.count).isEqualTo(3)
        val picked = buildProgressState(runs, ActivityFilter.ALL, ProgressPeriod.WEEKLY, ProgressMetric.RUNS, 10, now, zone)
        assertThat(picked.selected.count).isEqualTo(1)
        assertThat(picked.bars.last().value).isEqualTo(2.0)
        assertThat(buildProgressState(runs, ActivityFilter.ALL, ProgressPeriod.WEEKLY, ProgressMetric.RUNS, 99, now, zone).selectedIndex).isEqualTo(11)
    }

    @Test fun labelsAndAxis() {
        val s = buildProgressState(emptyList(), ActivityFilter.ALL, ProgressPeriod.WEEKLY, ProgressMetric.TIME, null, now, zone)
        assertThat(s.bars.last().label).isEqualTo("23 Nov")
        assertThat(s.bars[10].label).isEqualTo(" ")
        val m = buildProgressState(emptyList(), ActivityFilter.ALL, ProgressPeriod.MONTHLY, ProgressMetric.TIME, null, now, zone)
        assertThat(m.bars.last().label).isEqualTo("Nov")
        assertThat(m.isEmpty).isTrue()
        assertThat(ProgressMetric.DISTANCE.formatAxis(2_500.0, 5_000.0)).isEqualTo("2.5")
        assertThat(ProgressMetric.DISTANCE.formatAxis(20_000.0, 40_000.0)).isEqualTo("20")
        assertThat(ProgressMetric.RUNS.formatAxis(3.0, 5.0)).isEqualTo("3")
    }

    @Test fun recentIsNewestFirstAndCapped() {
        val runs = (1..8).map { run(at(2026, 11, it), 1.0) }
        val s = buildProgressState(runs, ActivityFilter.ALL, ProgressPeriod.MONTHLY, ProgressMetric.DISTANCE, null, now, zone)
        assertThat(s.recent).hasSize(RECENT_COUNT)
        assertThat(s.recent.first().startedAtMillis).isEqualTo(at(2026, 11, 8))
    }

    @Test fun viewModelFiltersAndSelects() = runTest(mainRule.testDispatcher) {
        val clock = FakeClock(now, zone)
        val repo = FakeRunRepository(clock)
        // use import path so runs are FINISHED
        listOf(run(at(2026, 11, 24), 5.0), run(at(2026, 11, 25), 3.0, ActivityType.WALK)).forEach {
            repo.importRunGraph(
                com.example.running.core.model.RunGraph(it, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            )
        }
        val vm = ProgressViewModel(repo, clock, TestAppDispatchers(mainRule.testDispatcher))
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        assertThat(vm.state.value!!.allTime.count).isEqualTo(2)
        vm.setFilter(ActivityFilter.WALK)
        advanceUntilIdle()
        assertThat(vm.state.value!!.allTime.count).isEqualTo(1)
        vm.setPeriod(ProgressPeriod.MONTHLY)
        vm.selectBar(3)
        advanceUntilIdle()
        assertThat(vm.state.value!!.selectedIndex).isEqualTo(3)
        vm.setFilter(ActivityFilter.ALL)
        advanceUntilIdle()
        assertThat(vm.state.value!!.selectedIndex).isEqualTo(11)
    }

    @Test fun personalBestRows() {
        val pb = PersonalBests(
            efforts = BestEffortDistance.entries.associateWith { d ->
                if (d == BestEffortDistance.K5) {
                    EffortRecord("r1", "Morning Run", at(2026, 11, 24), BestEffort(d, 1_500_000, 0, 1_500_000, 0.0))
                } else null
            },
            longestDistance = RunRecord("r2", "Long Run", at(2026, 11, 20), 21_000.0, 7_200_000),
            longestDuration = null,
        )
        val rows = buildPbRows(pb, zone.id)
        assertThat(rows.map { it.kind }).containsExactly(
            *BestEffortDistance.entries.map { PbKind.Effort(it) }.toTypedArray(),
            PbKind.LongestDistance, PbKind.LongestDuration,
        ).inOrder()
        val k5 = rows.first { it.kind == PbKind.Effort(BestEffortDistance.K5) }
        assertThat(k5.value).isEqualTo("25:00")
        assertThat(k5.pace).isEqualTo("5:00 /km")
        assertThat(k5.runId).isEqualTo("r1")
        assertThat(k5.date).isEqualTo("24 Nov 2026")
        assertThat(rows.first { it.kind == PbKind.Effort(BestEffortDistance.K1) }.runId).isNull()
        assertThat(rows.first { it.kind == PbKind.LongestDistance }.value).isEqualTo("21.00 km")
        assertThat(rows.first { it.kind == PbKind.LongestDuration }.value).isNull()
    }
}
