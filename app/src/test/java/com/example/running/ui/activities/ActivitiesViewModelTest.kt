package com.example.running.ui.activities

import app.cash.turbine.test
import com.example.running.core.demo.SyntheticRuns
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.ActivityType
import com.example.running.core.model.PointKind
import com.example.running.core.model.TrackPoint
import com.example.running.testing.FakeClock
import com.example.running.testing.FakeRunRepository
import com.example.running.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ActivitiesViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    // Wednesday 25 Nov 2026, 10:00 local
    private val now = ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val clock = FakeClock(now, zone)
    private val repo = FakeRunRepository(clock)

    private fun at(d: Int, h: Int) = ZonedDateTime.of(2026, 11, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private suspend fun seed(id: String, start: Long, walk: Boolean = false, km: Double = 5.0) {
        repo.importRunGraph(
            SyntheticRuns.runGraph(id, start, zone.id, SyntheticRuns.Spec(distanceM = km * 1000, walkOnly = walk, paceSecPerKm = if (walk) 720.0 else 330.0)),
        )
    }

    private fun vm() = ActivitiesViewModel(repo, clock)

    @Test fun weekStripCountsOnlyThisWeekAndMarksToday() = runTest(mainRule.testDispatcher) {
        seed("a", at(23, 7))   // Mon this week
        seed("b", at(25, 7))   // Wed (today)
        seed("c", at(16, 7))   // last week
        vm().state.test {
            var s = awaitItem()
            while (s.loading) s = awaitItem()
            assertThat(s.cards).hasSize(3)
            val week = s.week!!
            assertThat(week.count).isEqualTo(2)
            assertThat(week.days).hasSize(7)
            assertThat(week.days.filter { it.hasActivity }.map { it.dayIndex }).containsExactly(0, 2)
            assertThat(week.days.single { it.isToday }.dayIndex).isEqualTo(2)
            assertThat(week.days.maxOf { it.fraction }).isWithin(0.001f).of(1f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun filterRestrictsCards() = runTest(mainRule.testDispatcher) {
        seed("run", at(24, 7))
        seed("walk", at(25, 7), walk = true, km = 2.0)
        val model = vm()
        model.state.test {
            var s = awaitItem()
            while (s.loading || s.cards.size < 2) s = awaitItem()
            model.setFilter(ActivityFilter.WALK)
            while (s.filter != ActivityFilter.WALK || s.cards.size != 1) s = awaitItem()
            assertThat(s.cards.single().type).isEqualTo(ActivityType.WALK)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun cardsAreFormattedAndNewestFirst() = runTest(mainRule.testDispatcher) {
        seed("old", at(20, 7))
        seed("new", at(25, 7))
        vm().state.test {
            var s = awaitItem()
            while (s.loading) s = awaitItem()
            assertThat(s.cards.map { it.id }).containsExactly("new", "old").inOrder()
            assertThat(s.cards[0].whenText).isEqualTo("Today")
            assertThat(s.cards[0].pace).matches("\\d+:\\d{2}")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun thumbnailRouteThinsAndKeepsEndpoints() {
        fun p(i: Int) = TrackPoint(
            "r", PointKind.FILTERED, null, true, i * 1000L, null, 12.0 + i * 1e-4, 77.0, null, null,
            null, null, null, null, null, false,
        )
        assertThat(thumbnailRoute(listOf(p(0)))).isNull()
        val route = thumbnailRoute((0 until 1000).map { p(it) }, maxPoints = 50)!!
        assertThat(route.points.size).isAtMost(52)
        assertThat(route.points.first().lat).isEqualTo(12.0)
        assertThat(route.points.last().lat).isEqualTo(12.0 + 999 * 1e-4)
    }

    @Test fun routeForIsCached() = runTest(mainRule.testDispatcher) {
        seed("a", at(25, 7))
        val model = vm()
        val first = model.routeFor("a")
        assertThat(first).isNotNull()
        assertThat(model.routeFor("a")).isSameInstanceAs(first)
    }
}
