package com.example.running.location

import com.example.running.core.model.LocationEngine.PLATFORM
import com.example.running.core.model.LocationEngine.PLAY
import com.example.running.core.model.LocationEngineMode
import com.example.running.recording.SchedulerClock
import com.example.running.recording.straightFixes
import com.example.running.testing.FakeLocationSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationEngineManagerTest {

    private fun TestScope.collect(manager: LocationEngineManager, mode: LocationEngineMode, dual: Boolean, millis: Long): List<EngineEvent> {
        val events = mutableListOf<EngineEvent>()
        backgroundScope.launch { manager.stream(mode, dual).toList(events) }
        advanceTimeBy(millis)
        return events
    }

    @Test fun autoFallsBackWhenGoogleGoesSilent() = runTest {
        val play = FakeLocationSource(PLAY, straightFixes(60, PLAY)).silenceAfter(10_000)
        val platform = FakeLocationSource(PLATFORM, straightFixes(60, PLATFORM, lonOffset = 1e-5))
        val manager = LocationEngineManager(listOf(play, platform), SchedulerClock(testScheduler))

        val events = collect(manager, LocationEngineMode.AUTO, dual = false, millis = 30_000)

        val switched = events.filterIsInstance<EngineEvent.Switched>()
        assertThat(switched).containsExactly(EngineEvent.Switched(PLAY, PLATFORM, fallbackActive = true))
        val fixes = events.filterIsInstance<EngineEvent.Fix>()
        assertThat(fixes.all { it.isPrimary }).isTrue()               // not dual: secondary fixes are not emitted
        val switchIndex = events.indexOf(switched.single())
        assertThat(events.take(switchIndex).filterIsInstance<EngineEvent.Fix>().all { it.fix.engine == PLAY }).isTrue()
        assertThat(events.drop(switchIndex).filterIsInstance<EngineEvent.Fix>().all { it.fix.engine == PLATFORM }).isTrue()
        // Last PLAY fix at 10 s, silence threshold 8 s: the switch lands at ~18-19 s.
        val firstPlatform = events.drop(switchIndex).filterIsInstance<EngineEvent.Fix>().first().fix
        assertThat(firstPlatform.timeMillis - straightFixes(1).first().timeMillis).isIn(18_000L..19_000L)
    }

    @Test fun autoWithoutPlayServicesUsesBuiltInOnly() = runTest {
        val play = FakeLocationSource(PLAY, straightFixes(20, PLAY), available = false)
        val platform = FakeLocationSource(PLATFORM, straightFixes(20, PLATFORM))
        val manager = LocationEngineManager(listOf(play, platform), SchedulerClock(testScheduler))
        assertThat(manager.preferredEngine(LocationEngineMode.AUTO)).isEqualTo(PLATFORM)

        val events = collect(manager, LocationEngineMode.AUTO, dual = false, millis = 10_000)

        assertThat(events.filterIsInstance<EngineEvent.Switched>()).isEmpty()
        assertThat(events.filterIsInstance<EngineEvent.Unavailable>()).isEmpty()
        assertThat(events.filterIsInstance<EngineEvent.Fix>().map { it.fix.engine }.toSet()).containsExactly(PLATFORM)
    }

    @Test fun googleModeIsFixedEvenWhenItFails() = runTest {
        val play = FakeLocationSource(PLAY, straightFixes(20, PLAY)).failAfter(3)
        val platform = FakeLocationSource(PLATFORM, straightFixes(60, PLATFORM))
        val manager = LocationEngineManager(listOf(play, platform), SchedulerClock(testScheduler))

        val events = collect(manager, LocationEngineMode.GOOGLE, dual = false, millis = 30_000)

        assertThat(events.filterIsInstance<EngineEvent.Switched>()).isEmpty()
        val engines = events.filterIsInstance<EngineEvent.Fix>().map { it.fix.engine }.toSet()
        assertThat(engines).containsExactly(PLAY)
        // Failed updates are retried after the back-off: more than the first 3 fixes arrive.
        assertThat(events.filterIsInstance<EngineEvent.Fix>().size).isGreaterThan(3)
    }

    @Test fun googleModeWithoutPlayServicesReportsUnavailable() = runTest {
        val play = FakeLocationSource(PLAY, emptyList(), available = false)
        val platform = FakeLocationSource(PLATFORM, straightFixes(10, PLATFORM))
        val manager = LocationEngineManager(listOf(play, platform), SchedulerClock(testScheduler))

        val events = collect(manager, LocationEngineMode.GOOGLE, dual = false, millis = 5_000)

        assertThat(events.first()).isEqualTo(EngineEvent.Unavailable(PLAY))
        assertThat(events.filterIsInstance<EngineEvent.Fix>()).isEmpty()
    }

    @Test fun autoSwitchesAtOnceWhenGoogleFails() = runTest {
        val play = FakeLocationSource(PLAY, straightFixes(60, PLAY)).failAfter(5)
        val platform = FakeLocationSource(PLATFORM, straightFixes(60, PLATFORM))
        val manager = LocationEngineManager(listOf(play, platform), SchedulerClock(testScheduler))

        val events = collect(manager, LocationEngineMode.AUTO, dual = false, millis = 8_000)

        assertThat(events.filterIsInstance<EngineEvent.Switched>()).containsExactly(EngineEvent.Switched(PLAY, PLATFORM, true))
    }

    @Test fun dualModeEmitsBothEnginesWithPrimaryFlag() = runTest {
        val play = FakeLocationSource(PLAY, straightFixes(10, PLAY))
        val platform = FakeLocationSource(PLATFORM, straightFixes(10, PLATFORM))
        val manager = LocationEngineManager(listOf(play, platform), SchedulerClock(testScheduler))

        val fixes = collect(manager, LocationEngineMode.BUILT_IN, dual = true, millis = 9_500).filterIsInstance<EngineEvent.Fix>()

        assertThat(fixes.filter { it.fix.engine == PLATFORM }.all { it.isPrimary }).isTrue()
        assertThat(fixes.filter { it.fix.engine == PLAY }.none { it.isPrimary }).isTrue()
        assertThat(fixes.count { it.fix.engine == PLAY }).isEqualTo(10)
        assertThat(fixes.count { it.fix.engine == PLATFORM }).isEqualTo(10)
    }
}
