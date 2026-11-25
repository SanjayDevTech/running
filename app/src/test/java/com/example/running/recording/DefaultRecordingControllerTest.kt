package com.example.running.recording

import com.example.running.analysis.DefaultLiveMetricsFactory
import com.example.running.analysis.DefaultRunAnalyzer
import com.example.running.analysis.DefaultRunNamer
import com.example.running.core.contracts.LiveMetricsFactory
import com.example.running.core.model.ActivityType
import com.example.running.core.model.AppSettings
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.PointKind
import com.example.running.core.model.PressureSample
import com.example.running.core.model.RecordingEvent
import com.example.running.core.model.RecordingState
import com.example.running.core.model.RunAnalysis
import com.example.running.core.model.RunStats
import com.example.running.core.model.RunStatus
import com.example.running.core.model.SensorType
import com.example.running.core.model.SensorSample
import com.example.running.core.model.StepCounterReading
import com.example.running.core.model.StepEvent
import com.example.running.core.util.haversineMeters
import com.example.running.location.LocationEngineManager
import com.example.running.testing.FakeLocationSource
import com.example.running.testing.FakeMapOfflineManager
import com.example.running.testing.FakePressureSource
import com.example.running.testing.FakeRunAnalyzer
import com.example.running.testing.FakeRunRepository
import com.example.running.testing.FakeSettingsRepository
import com.example.running.testing.FakeStepSource
import com.example.running.testing.TestAppDispatchers
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRecordingControllerTest {

    internal class Harness(
        scope: TestScope,
        playFixes: List<com.example.running.core.model.LocationFix> = straightFixes(300),
        platformFixes: List<com.example.running.core.model.LocationFix>? = null,
        settings: AppSettings = AppSettings(engineMode = LocationEngineMode.GOOGLE),
        steps: List<StepEvent> = emptyList(),
        hasStepDetector: Boolean = true,
        pressure: List<PressureSample> = emptyList(),
        /** Real live metrics (B's DefaultLiveMetrics) instead of the haversine fake, for replay tests. */
        realMetrics: LiveMetricsFactory? = null,
        counter: List<StepCounterReading> = emptyList(),
        bootedAtMillis: Long = T0,
        val platform: FakeRecordingPlatform = FakeRecordingPlatform(),
        val store: InMemoryRecoveryStore = InMemoryRecoveryStore(),
        val analyzer: FakeRunAnalyzer = FakeRunAnalyzer { input ->
            RunAnalysis(
                stats = RunStats(distanceM = 123.0, bounds = GeoBounds.of(input.rawPoints.map { GeoPoint(it.lat, it.lon) })),
                suggestedType = ActivityType.RUN, suggestedName = "Morning Run",
                filteredPoints = input.rawPoints.map { it.copy(kind = PointKind.FILTERED) },
                splits = emptyList(), segments = emptyList(), bestEfforts = emptyList(),
                learnedRunStrideM = 1.12, learnedWalkStrideM = null, analysisVersion = 1,
            )
        },
    ) {
        val clock = SchedulerClock(scope.testScheduler, bootedAtMillis = bootedAtMillis)
        val dispatchers = TestAppDispatchers(StandardTestDispatcher(scope.testScheduler))
        val repo = FakeRunRepository()
        val settingsRepo = FakeSettingsRepository(settings)
        val offline = FakeMapOfflineManager()
        val metrics = FakeLiveMetricsFactory()
        val play = FakeLocationSource(LocationEngine.PLAY, playFixes)
        val platformSource = FakeLocationSource(LocationEngine.PLATFORM, platformFixes.orEmpty(), available = platformFixes != null)
        val controller = DefaultRecordingController(
            scope = scope.backgroundScope,
            clock = clock,
            dispatchers = dispatchers,
            repository = repo,
            settings = settingsRepo,
            engines = LocationEngineManager(listOf(play, platformSource), clock),
            stepSource = FakeStepSource(steps, hasStepDetector = hasStepDetector, hasStepCounter = true, counter = counter),
            pressureSource = FakePressureSource(pressure, isAvailable = pressure.isNotEmpty()),
            liveMetricsFactory = realMetrics ?: metrics,
            finalizer = RunFinalizer(repo, settingsRepo, analyzer, offline, scope.backgroundScope, dispatchers),
            platform = platform,
            recoveryStore = store,
        )
        val events = mutableListOf<RecordingEvent>()

        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { controller.events.toList(events) }
        }

        val state get() = controller.state.value
        fun graph(runId: String) = repo.graphs.value.getValue(runId)
    }

    private fun TestScope.warmUpAndStart(h: Harness): String {
        h.controller.beginWarmUp()
        advanceTimeBy(1_500)
        h.controller.start()
        runCurrent()
        return (h.state as RecordingState.Recording).runId
    }

    private fun TestScope.stop(h: Harness) {
        h.controller.stop()
        advanceTimeBy(2_000)
    }

    // ------------------------------------------------------------------ start gate

    @Test fun weakGpsStartsImmediatelyWithWarning() = runTest {
        val h = Harness(this, playFixes = straightFixes(60, accuracyM = 30f))
        h.controller.beginWarmUp()
        advanceTimeBy(2_000)
        val gps = (h.state as RecordingState.WarmingUp).gps
        assertThat(gps.startAllowed).isTrue()
        assertThat(gps.weakSignalWarning).isTrue()
        h.controller.start()
        runCurrent()
        assertThat(h.state).isInstanceOf(RecordingState.Recording::class.java)
    }

    @Test fun noFixStillStarts() = runTest {
        val h = Harness(this, playFixes = emptyList())
        h.controller.beginWarmUp()
        runCurrent()
        assertThat((h.state as RecordingState.WarmingUp).gps.startAllowed).isTrue()
        h.controller.start()
        runCurrent()
        assertThat(h.state).isInstanceOf(RecordingState.Recording::class.java)
    }

    @Test fun endWarmUpReturnsToIdle() = runTest {
        val h = Harness(this)
        h.controller.beginWarmUp()
        advanceTimeBy(2_000)
        h.controller.endWarmUp()
        runCurrent()
        assertThat(h.state).isEqualTo(RecordingState.Idle)
        advanceTimeBy(5_000)
        assertThat(h.state).isEqualTo(RecordingState.Idle)
    }

    // ------------------------------------------------------------------ record, pause, stop

    @Test fun recordsPausesAndSavesThroughThePipeline() = runTest {
        val h = Harness(this)
        val runId = warmUpAndStart(h)
        val startedAt = (h.state as RecordingState.Recording).startedAtMillis
        assertThat(h.platform.serviceStarts).isEqualTo(1)
        assertThat(h.graph(runId).run.status).isEqualTo(RunStatus.ACTIVE)
        assertThat(h.graph(runId).run.engineMode).isEqualTo(LocationEngineMode.GOOGLE)
        assertThat(h.graph(runId).run.startEngine).isEqualTo(LocationEngine.PLAY)

        advanceTimeBy(10_000)
        assertThat(h.graph(runId).points).isNotEmpty() // flushed in 5 s batches
        h.controller.pause()
        runCurrent()
        assertThat((h.state as RecordingState.Recording).paused).isTrue()
        advanceTimeBy(10_000)
        h.controller.resume()
        runCurrent()
        assertThat((h.state as RecordingState.Recording).paused).isFalse()
        advanceTimeBy(10_000)
        stop(h)

        assertThat(h.events).contains(RecordingEvent.RunSaved(runId))
        assertThat(h.state).isEqualTo(RecordingState.Idle)
        val g = h.graph(runId)
        assertThat(g.run.status).isEqualTo(RunStatus.FINISHED)
        assertThat(g.run.name).isEqualTo("Morning Run")
        assertThat(g.pauses).hasSize(1)
        val pause = g.pauses.single()
        assertThat(pause.startMillis - startedAt).isIn(9_900L..10_100L)
        assertThat(pause.endMillis!! - pause.startMillis).isIn(9_900L..10_100L)

        val raw = g.points.filter { it.kind == PointKind.RAW }
        assertThat(raw.size).isIn(29..31)
        assertThat(raw.all { it.isPrimary && it.engine == LocationEngine.PLAY }).isTrue()
        assertThat(raw.all { it.timeMillis >= startedAt }).isTrue()
        val inPause = raw.filter { it.timeMillis > pause.startMillis && it.timeMillis < pause.endMillis!! }
        assertThat(inPause).isNotEmpty()
        assertThat(inPause.all { it.paused }).isTrue()
        assertThat(raw.filter { it.timeMillis < pause.startMillis || it.timeMillis > pause.endMillis!! }.none { it.paused }).isTrue()

        // Analysis input: closed pause, primary RAW points; then strides and offline area.
        val input = h.analyzer.inputs.single()
        assertThat(input.pauses.single().endMillis).isEqualTo(pause.endMillis)
        assertThat(h.settingsRepo.state.value.profile.learnedRunStrideM).isEqualTo(1.12)
        assertThat(h.offline.downloads.map { it.first }).containsExactly(runId)
    }

    @Test fun pausedMovementIsNotCountedLive() = runTest {
        val h = Harness(this)
        warmUpAndStart(h)
        advanceTimeBy(10_000)
        h.controller.pause()
        runCurrent()
        val before = (h.state as RecordingState.Recording).stats.distanceM
        assertThat(before).isGreaterThan(0.0)
        advanceTimeBy(10_000)
        val whilePaused = (h.state as RecordingState.Recording).stats.distanceM
        assertThat(whilePaused).isEqualTo(before)
        val metrics = h.metrics.created.single()
        assertThat(metrics.fixes.filter { it.second }).isNotEmpty()
        assertThat(metrics.pauseChanges.map { it.first }).containsExactly(true)
        h.controller.resume()
        advanceTimeBy(5_000)
        assertThat((h.state as RecordingState.Recording).stats.distanceM).isGreaterThan(whilePaused)
    }

    @Test fun stepAndPressureSamplesArePersisted() = runTest {
        val steps = (0 until 20).map { StepEvent(T0 + 2_000 + it * 400L) }
        val pressure = (0 until 10).map { PressureSample(T0 + 2_000 + it * 1_000L, 912.0 + it * 0.01) }
        val h = Harness(this, steps = steps, pressure = pressure)
        val runId = warmUpAndStart(h)
        advanceTimeBy(15_000)
        stop(h)
        val samples = h.graph(runId).sensorSamples
        assertThat(samples.count { it.type == SensorType.STEP_DETECTOR }).isEqualTo(20)
        assertThat(samples.filter { it.type == SensorType.STEP_DETECTOR }.all { it.value == 1.0 }).isTrue()
        assertThat(samples.count { it.type == SensorType.PRESSURE }).isEqualTo(10)
        assertThat(h.metrics.created.single().steps).hasSize(20)
    }

    @Test fun stepCounterOnlyDevicesStoreCumulativeCounts() = runTest {
        val steps = listOf(StepEvent(T0 + 2_000, 3), StepEvent(T0 + 3_000, 2), StepEvent(T0 + 4_000, 4))
        val h = Harness(this, steps = steps, hasStepDetector = false)
        val runId = warmUpAndStart(h)
        advanceTimeBy(6_000)
        stop(h)
        val counter = h.graph(runId).sensorSamples.filter { it.type == SensorType.STEP_COUNTER }
        assertThat(counter.map { it.value }).containsExactly(3.0, 5.0, 9.0).inOrder()
    }

    @Test fun saveFailureLeavesRunActiveForRecovery() = runTest {
        val h = Harness(this, analyzer = FakeRunAnalyzer { error("boom") })
        val runId = warmUpAndStart(h)
        advanceTimeBy(5_000)
        stop(h)
        assertThat(h.events).contains(RecordingEvent.Failure(RecordingMessage.SAVE_FAILED.name))
        assertThat(h.state).isEqualTo(RecordingState.Idle)
        assertThat(h.graph(runId).run.status).isEqualTo(RunStatus.ACTIVE)
    }

    @Test fun refusedServiceStartIsReportedButRecordingContinues() = runTest {
        val h = Harness(this, platform = FakeRecordingPlatform(serviceAllowed = false))
        warmUpAndStart(h)
        assertThat(h.events).contains(RecordingEvent.Failure(RecordingMessage.SERVICE_REFUSED.name))
        assertThat(h.state).isInstanceOf(RecordingState.Recording::class.java)
    }

    // ------------------------------------------------------------------ engines

    @Test fun autoFallbackMidRunMarksTheRun() = runTest {
        val h = Harness(
            this,
            playFixes = straightFixes(200).let { all -> all }, // silenced below
            platformFixes = straightFixes(200, LocationEngine.PLATFORM, lonOffset = 1e-5),
            settings = AppSettings(engineMode = LocationEngineMode.AUTO),
        )
        h.play.silenceAfter(10_000)
        val runId = warmUpAndStart(h)
        advanceTimeBy(30_000)
        assertThat(h.events).contains(RecordingEvent.EngineSwitched(LocationEngine.PLAY, LocationEngine.PLATFORM))
        assertThat(h.graph(runId).run.engineFallback).isTrue()
        val gps = (h.state as RecordingState.Recording).gps
        assertThat(gps.engine).isEqualTo(LocationEngine.PLATFORM)
        assertThat(gps.fallbackActive).isTrue()
        stop(h)
        val raw = h.graph(runId).points.filter { it.kind == PointKind.RAW }
        assertThat(raw.map { it.engine }.toSet()).containsExactly(LocationEngine.PLAY, LocationEngine.PLATFORM)
        assertThat(raw.all { it.isPrimary }).isTrue()
    }

    @Test fun devDualModeStoresBothEngines() = runTest {
        val h = Harness(
            this,
            platformFixes = straightFixes(200, LocationEngine.PLATFORM, lonOffset = 1e-5),
            settings = AppSettings(engineMode = LocationEngineMode.GOOGLE, devModeUnlocked = true, devDualEngine = true),
        )
        val runId = warmUpAndStart(h)
        advanceTimeBy(10_000)
        stop(h)
        val g = h.graph(runId)
        assertThat(g.run.devDualEngine).isTrue()
        val raw = g.points.filter { it.kind == PointKind.RAW }
        assertThat(raw.filter { it.engine == LocationEngine.PLAY }.all { it.isPrimary }).isTrue()
        assertThat(raw.filter { it.engine == LocationEngine.PLATFORM }.none { it.isPrimary }).isTrue()
        assertThat(raw.count { it.engine == LocationEngine.PLATFORM }).isAtLeast(9)
        // Only primary fixes drive the live metrics.
        assertThat(h.metrics.created.single().fixes.all { it.first.engine == LocationEngine.PLAY }).isTrue()
    }

    @Test fun googleUnavailableIsReported() = runTest {
        val h = Harness(this, playFixes = emptyList())
        h.play.available = false
        h.controller.beginWarmUp()
        advanceTimeBy(1_000)
        assertThat(h.events).contains(RecordingEvent.Failure(RecordingMessage.PLAY_UNAVAILABLE.name))
    }

    // ------------------------------------------------------------------ crash recovery

    /** An ACTIVE run that was recording 60 s of fixes, ending 9 min before "now" (T0). */
    private suspend fun Harness.seedInterruptedRun(paused: Boolean = false): String {
        val runId = "interrupted"
        val startedAt = T0 - 600_000
        repo.createActiveRun(runId, startedAt, "Asia/Kolkata", LocationEngineMode.GOOGLE, LocationEngine.PLAY, false)
        repo.appendRawPoints(straightFixes(60, startMillis = startedAt).map { it.toRawPoint(runId, isPrimary = true, paused = false) })
        if (paused) repo.startPause(runId, startedAt + 59_500)
        return runId
    }

    @Test fun autoResumesWithBackgroundLocation() = runTest {
        val h = Harness(this, playFixes = emptyList(), platform = FakeRecordingPlatform(backgroundLocation = true))
        val runId = h.seedInterruptedRun()
        h.controller.checkForInterruptedRun()
        runCurrent()
        val rec = h.state as RecordingState.Recording
        assertThat(rec.runId).isEqualTo(runId)
        assertThat(rec.stats.distanceM).isWithin(1.0).of(59 * 3.0)   // replayed from the database
        assertThat(h.platform.serviceStarts).isEqualTo(1)
        assertThat(h.store.autoResumeAttempts(runId)).isEqualTo(1)
        assertThat(h.graph(runId).pauses).isEmpty()                    // auto-resume: no gap pause
        advanceTimeBy(10_000)
        stop(h)
        assertThat(h.graph(runId).run.status).isEqualTo(RunStatus.FINISHED)
        assertThat(h.store.autoResumeAttempts(runId)).isEqualTo(0)
    }

    @Test fun oldInterruptionAsksInsteadOfAutoResuming() = runTest {
        val h = Harness(this, platform = FakeRecordingPlatform(backgroundLocation = true))
        val runId = "stale"
        val startedAt = T0 - 2 * 3_600_000L - 60_000
        h.repo.createActiveRun(runId, startedAt, "Asia/Kolkata", LocationEngineMode.GOOGLE, LocationEngine.PLAY, false)
        h.repo.appendRawPoints(straightFixes(60, startMillis = startedAt).map { it.toRawPoint(runId, isPrimary = true, paused = false) })
        h.controller.checkForInterruptedRun()   // last data is 2 h old
        runCurrent()
        assertThat((h.state as RecordingState.NeedsRecoveryDecision).runId).isEqualTo(runId)
        assertThat(h.platform.serviceStarts).isEqualTo(0)
        assertThat(h.store.autoResumeAttempts(runId)).isEqualTo(0)

        h.controller.resumeInterrupted(runId)   // the user chose to resume: the 2 h gap is a pause
        runCurrent()
        val gap = h.graph(runId).pauses.single()
        assertThat(gap.startMillis).isEqualTo(startedAt + 59_000)
        assertThat(gap.endMillis).isEqualTo(T0)
    }

    @Test fun asksWithoutBackgroundLocationThenResumesWithGapAsPause() = runTest {
        val h = Harness(this, platform = FakeRecordingPlatform(backgroundLocation = false))
        val runId = h.seedInterruptedRun()
        h.controller.checkForInterruptedRun()
        val ask = h.state as RecordingState.NeedsRecoveryDecision
        assertThat(ask.runId).isEqualTo(runId)
        assertThat(ask.distanceM).isWithin(1.0).of(59 * 3.0)
        assertThat(h.platform.serviceStarts).isEqualTo(0)
        assertThat(h.platform.alertsPosted).isEqualTo(0)  // app open: the screen asks, no notification

        h.controller.resumeInterrupted(runId)
        runCurrent()
        assertThat(h.state).isInstanceOf(RecordingState.Recording::class.java)
        assertThat(h.platform.serviceStarts).isEqualTo(1)
        val gap = h.graph(runId).pauses.single()
        assertThat(gap.startMillis).isEqualTo(T0 - 600_000 + 59_000)
        assertThat(gap.endMillis).isEqualTo(T0)
        assertThat((h.state as RecordingState.Recording).paused).isFalse()
    }

    @Test fun resumeKeepsAnOpenPauseOpen() = runTest {
        val h = Harness(this, platform = FakeRecordingPlatform(backgroundLocation = true))
        val runId = h.seedInterruptedRun(paused = true)
        h.controller.checkForInterruptedRun()
        runCurrent()
        val rec = h.state as RecordingState.Recording
        assertThat(rec.paused).isTrue()
        h.controller.resume()
        runCurrent()
        assertThat(h.graph(runId).pauses.single().endMillis).isEqualTo(T0)
    }

    @Test fun finishInterruptedEndsAtLastRecordedData() = runTest {
        val h = Harness(this)
        val runId = h.seedInterruptedRun()
        h.controller.checkForInterruptedRun()
        h.controller.finishInterrupted(runId)
        advanceTimeBy(1_000)
        val g = h.graph(runId)
        assertThat(g.run.status).isEqualTo(RunStatus.FINISHED)
        assertThat(g.run.endedAtMillis).isEqualTo(T0 - 600_000 + 59_000)
        assertThat(h.events).contains(RecordingEvent.RunSaved(runId))
        assertThat(h.state).isEqualTo(RecordingState.Idle)
    }

    @Test fun refusedServiceStartFallsBackToPrompt() = runTest {
        val h = Harness(this, platform = FakeRecordingPlatform(backgroundLocation = true, serviceAllowed = false))
        val runId = h.seedInterruptedRun()
        h.controller.checkForInterruptedRun()
        assertThat((h.state as RecordingState.NeedsRecoveryDecision).runId).isEqualTo(runId)
    }

    @Test fun autoResumeIsCappedPerRun() = runTest {
        val store = InMemoryRecoveryStore()
        val h = Harness(this, platform = FakeRecordingPlatform(backgroundLocation = true), store = store)
        val runId = h.seedInterruptedRun()
        repeat(RecoveryPolicy.MAX_AUTO_RESUMES) { store.recordAutoResume(runId) }
        h.controller.checkForInterruptedRun()
        assertThat(h.state).isInstanceOf(RecordingState.NeedsRecoveryDecision::class.java)
    }

    @Test fun serviceRestartWithoutBackgroundLocationPostsAlert() = runTest {
        val h = Harness(this, platform = FakeRecordingPlatform(backgroundLocation = false))
        h.seedInterruptedRun()
        assertThat(h.controller.recoverFromService()).isFalse()
        assertThat(h.state).isInstanceOf(RecordingState.NeedsRecoveryDecision::class.java)
        assertThat(h.platform.alertsPosted).isEqualTo(1)
    }

    @Test fun serviceRestartWithBackgroundLocationResumesWithoutStartingAgain() = runTest {
        val h = Harness(this, platform = FakeRecordingPlatform(backgroundLocation = true))
        val runId = h.seedInterruptedRun()
        assertThat(h.controller.recoverFromService()).isTrue()
        assertThat((h.state as RecordingState.Recording).runId).isEqualTo(runId)
        assertThat(h.platform.serviceStarts).isEqualTo(0)
    }

    @Test fun noActiveRunMeansNothingToRecover() = runTest {
        val h = Harness(this, platform = FakeRecordingPlatform(backgroundLocation = true))
        h.controller.checkForInterruptedRun()
        assertThat(h.state).isEqualTo(RecordingState.Idle)
        assertThat(h.controller.recoverFromService()).isFalse()
    }

    @Test fun recoveryInterruptsAWarmUp() = runTest {
        val h = Harness(this)
        h.controller.beginWarmUp()
        advanceTimeBy(1_000)
        val runId = h.seedInterruptedRun()
        h.controller.checkForInterruptedRun()
        assertThat((h.state as RecordingState.NeedsRecoveryDecision).runId).isEqualTo(runId)
        advanceTimeBy(3_000)
        assertThat(h.state).isInstanceOf(RecordingState.NeedsRecoveryDecision::class.java)
    }

    @Test fun liveRouteFollowsAcceptedFixes() = runTest {
        val h = Harness(this)
        warmUpAndStart(h)
        advanceTimeBy(5_000)
        val route = h.controller.liveRoute.value!!
        assertThat(route.points.size).isAtLeast(4)
        val first = route.points.first()
        val last = route.points.last()
        assertThat(haversineMeters(first.lat, first.lon, last.lat, last.lon)).isGreaterThan(8.0)
        assertThat(h.controller.userLocation.value).isNotNull()
    }

    // ------------------------------------------------------------------ replay against the real LiveMetrics

    @Test fun realLiveMetricsLeaveMovementWhilePausedOut() = runTest {
        // 3 m/s for 160 s; the runner keeps moving through a 20 s pause, which must not count.
        val h = Harness(this, playFixes = straightFixes(170), realMetrics = DefaultLiveMetricsFactory())
        warmUpAndStart(h)
        advanceTimeBy(60_000)
        h.controller.pause()
        advanceTimeBy(20_000)
        h.controller.resume()
        advanceTimeBy(60_000)
        val stats = (h.state as RecordingState.Recording).stats
        assertThat(stats.pausedMillis).isWithin(1_500).of(20_000)
        assertThat(stats.distanceM).isWithin(20.0).of(120 * 3.0)
        assertThat(stats.avgPaceSecPerKm!!).isWithin(25.0).of(1_000 / 3.0)
        assertThat(stats.currentPaceSecPerKm!!).isWithin(25.0).of(1_000 / 3.0)
        // The route has no point from the paused stretch: no jump longer than a few seconds of running.
        val pts = h.controller.liveRoute.value!!.points
        val maxHop = pts.zipWithNext { a, b -> haversineMeters(a.lat, a.lon, b.lat, b.lon) }.max()
        assertThat(maxHop).isGreaterThan(20 * 3.0)                     // the line across the pause...
        val ds = pts.zipWithNext { a, b -> b.distanceM - a.distanceM }.max()
        assertThat(ds).isLessThan(10.0)                                 // ...adds no distance
    }

    @Test fun realLiveMetricsKeepPaceAfterAutoResumeGap() = runTest {
        // Recorded 0–59 s, process died, auto-resumed at T0 (600 s) with the runner still going at 3 m/s.
        val startedAt = T0 - 600_000
        val after = straightFixes(640, startMillis = startedAt).drop(600)
        val h = Harness(
            this, playFixes = after, realMetrics = DefaultLiveMetricsFactory(),
            platform = FakeRecordingPlatform(backgroundLocation = true),
        )
        val runId = h.seedInterruptedRun()
        h.controller.checkForInterruptedRun()
        runCurrent()
        assertThat((h.state as RecordingState.Recording).stats.distanceM).isWithin(10.0).of(59 * 3.0)
        advanceTimeBy(30_000)
        val stats = (h.state as RecordingState.Recording).stats
        assertThat(stats.distanceM).isWithin(30.0).of(630 * 3.0)
        assertThat(stats.avgPaceSecPerKm!!).isWithin(25.0).of(1_000 / 3.0)
        assertThat(stats.currentPaceSecPerKm!!).isWithin(25.0).of(1_000 / 3.0)
        stop(h)
        assertThat(h.graph(runId).run.status).isEqualTo(RunStatus.FINISHED)
    }

    // ------------------------------------------------------------------ resume gap (step counter)

    /** Recorded 0–59 s at 3 m/s and 180 spm with a boot-counter anchor at 59 s; the app died until T0 (120 s). */
    private suspend fun Harness.seedRunWithCounterAnchor(): String {
        val runId = "gap"
        val startedAt = T0 - 120_000
        repo.createActiveRun(runId, startedAt, "Asia/Kolkata", LocationEngineMode.GOOGLE, LocationEngine.PLAY, false)
        repo.appendRawPoints(straightFixes(60, startMillis = startedAt).map { it.toRawPoint(runId, isPrimary = true, paused = false) })
        val steps = (1..179).map { SensorSample(SensorType.STEP_DETECTOR, startedAt + it * 333L, 1.0) } // up to 59.6 s
        repo.appendSensorSamples(runId, steps + SensorSample(SensorType.STEP_COUNTER_BOOT, startedAt + 59_000, 10_000.0))
        return runId
    }

    @Test fun autoResumeBridgesTheGapWithStepCounterSteps() = runTest {
        val startedAt = T0 - 120_000
        // While dead the runner took 240 steps (the counter: anchor + 2 recorded after it + 240) on a
        // path longer than the straight line; recording goes on north at 3 m/s from T0.
        val h = Harness(
            this, playFixes = straightFixes(150, startMillis = startedAt).drop(120), realMetrics = DefaultLiveMetricsFactory(),
            platform = FakeRecordingPlatform(backgroundLocation = true),
            counter = listOf(StepCounterReading(T0 + 300, 10_242.0)), bootedAtMillis = T0 - 3_600_000,
            steps = (1..90).map { StepEvent(T0 + it * 333L) }, // still running at 180 spm after the resume
            analyzer = FakeRunAnalyzer { DefaultRunAnalyzer(DefaultRunNamer()).analyze(it) },
        )
        val runId = h.seedRunWithCounterAnchor()
        h.controller.checkForInterruptedRun()
        advanceTimeBy(20_000)
        val gap = h.graph(runId).sensorSamples.single { it.type == SensorType.RESUME_GAP }
        assertThat(gap.timeMillis).isEqualTo(T0)
        assertThat(gap.value).isWithin(1.0).of(240.0) // less a step already seen after the resume
        val stride = 1.0 // 3 m/s at 180 spm, learned live from the good stretch
        val stats = (h.state as RecordingState.Recording).stats
        assertThat(stats.distanceM).isWithin(20.0).of(59 * 3.0 + 240 * stride + 19 * 3.0)
        assertThat(h.controller.liveRoute.value!!.points.count { it.stepFilled }).isEqualTo(1)
        stop(h)
        // The saved run bridges the gap the same way.
        val saved = h.graph(runId).run.stats
        assertThat(saved.distanceM).isWithin(25.0).of(stats.distanceM)
        val filtered = h.graph(runId).points.filter { it.kind == PointKind.FILTERED }
        assertThat(filtered.filter { it.timeMillis > startedAt + 59_000 && it.timeMillis < T0 }.all { it.stepFilled && it.paceSecPerKm == null }).isTrue()
    }

    @Test fun autoResumeAfterRebootFallsBackToTheStraightLine() = runTest {
        val startedAt = T0 - 120_000
        val h = Harness(
            this, playFixes = straightFixes(150, startMillis = startedAt).drop(120), realMetrics = DefaultLiveMetricsFactory(),
            platform = FakeRecordingPlatform(backgroundLocation = true),
            counter = listOf(StepCounterReading(T0 + 300, 260.0)), bootedAtMillis = T0 - 3_600_000,
        )
        val runId = h.seedRunWithCounterAnchor()
        h.controller.checkForInterruptedRun()
        advanceTimeBy(20_000)
        assertThat(h.graph(runId).sensorSamples.single { it.type == SensorType.RESUME_GAP }.value).isEqualTo(-1.0)
        val stats = (h.state as RecordingState.Recording).stats
        assertThat(stats.distanceM).isWithin(15.0).of(139 * 3.0) // the straight line north
        assertThat(stats.avgPaceSecPerKm!!).isWithin(25.0).of(1_000 / 3.0)
    }

    @Test fun newRunsStoreTheBootCounterAsTheNextAnchor() = runTest {
        val h = Harness(this, counter = listOf(StepCounterReading(T0 + 2_000, 5_000.0), StepCounterReading(T0 + 8_000, 5_020.0)))
        val runId = warmUpAndStart(h)
        advanceTimeBy(15_000)
        stop(h)
        val boot = h.graph(runId).sensorSamples.filter { it.type == SensorType.STEP_COUNTER_BOOT }
        assertThat(boot.map { it.value }).containsExactly(5_000.0, 5_020.0).inOrder()
        assertThat(h.graph(runId).sensorSamples.none { it.type == SensorType.RESUME_GAP }).isTrue()
    }
}
