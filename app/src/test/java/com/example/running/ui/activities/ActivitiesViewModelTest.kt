package com.example.running.ui.activities

import app.cash.turbine.test
import com.example.running.core.demo.SyntheticRuns
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.ActivityType
import com.example.running.testing.FakeClock
import com.example.running.testing.FakeRunRepository
import com.example.running.testing.MainDispatcherRule
import com.example.running.testing.TestAppDispatchers
import com.example.running.core.model.MapRoute
import com.example.running.core.util.LruMap
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

    private fun vm(cacheSize: Int = 100) = ActivitiesViewModel(repo, clock, TestAppDispatchers(mainRule.testDispatcher), cacheSize)

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

    @Test fun thumbnailIsCached() = runTest(mainRule.testDispatcher) {
        seed("a", at(25, 7))
        val model = vm()
        val first = model.thumbnailFor("a")
        assertThat(first).isNotNull()
        assertThat(model.thumbnailFor("a")).isSameInstanceAs(first)
    }

    @Test fun thumbnailsAreThinAndTheCacheIsBounded() = runTest(mainRule.testDispatcher) {
        val ids = (1..6).map { "r$it" }
        ids.forEachIndexed { i, id -> seed(id, at(18 + i, 7), km = 12.0) } // ~12 km: far more than 240 points
        val model = vm(cacheSize = 4)
        ids.forEach { id ->
            val t = model.thumbnailFor(id)!!
            val full = MapRoute.fromTrackPoints(repo.getFilteredPoints(id))
            assertThat(full.points.size).isGreaterThan(240)
            // Line ends repeat one point each, so at most 240 + lines - 1 coordinates.
            assertThat(t.lines.sumOf { it.coords.size }).isAtMost(240 + t.lines.size - 1)
            assertThat(t.bounds).isEqualTo(full.bounds) // bounds of the full route, not of the thinned one
        }
        assertThat(model.cachedThumbnails).isEqualTo(4)
        assertThat(model.thumbnailFor("missing")).isNull() // nothing to draw: cached as null
        assertThat(model.cachedThumbnails).isEqualTo(4)
    }

    @Test fun lruMapDropsTheLeastRecentlyUsed() {
        val m = LruMap<String, Int?>(2)
        m["a"] = 1
        m["b"] = null
        m["a"] // reading a makes b the eldest
        m["c"] = 3
        assertThat(m.keys).containsExactly("a", "c")
    }
}
