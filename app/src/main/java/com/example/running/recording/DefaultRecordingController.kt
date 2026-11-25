package com.example.running.recording

import com.example.running.core.contracts.LiveMetrics
import com.example.running.core.contracts.LiveMetricsFactory
import com.example.running.core.contracts.PressureSource
import com.example.running.core.contracts.RecordingController
import com.example.running.core.contracts.StepSource
import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.GpsQuality
import com.example.running.core.model.GpsStatus
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.MapRoute
import com.example.running.core.model.RecordingEvent
import com.example.running.core.model.RecordingState
import com.example.running.core.model.Run
import com.example.running.core.model.SensorType
import com.example.running.core.util.AppDispatchers
import com.example.running.core.util.Clock
import com.example.running.core.util.newRunId
import com.example.running.data.repo.RunRepository
import com.example.running.data.settings.SettingsRepository
import com.example.running.location.EngineEvent
import com.example.running.location.GpsGate
import com.example.running.location.LocationEngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ControllerConfig(
    val tickMillis: Long = 1_000,
    /** RAW points and sensor samples are written in batches this far apart (and on pause/stop). */
    val flushMillis: Long = 5_000,
    /** A user-confirmed resume after a longer gap records the gap as a pause. */
    val manualResumeGapPauseMillis: Long = 60_000,
    /**
     * Auto-resume only when the last persisted data is at most this old. An older interruption (phone
     * died or rebooted mid-run, app opened hours later) goes to the resume/finish prompt instead of
     * silently restarting GPS with the whole gap counted as active time.
     */
    val autoResumeMaxGapMillis: Long = 10 * 60_000,
)

/**
 * Track A's [RecordingController]. Lives as long as the process (built by RecordingModule) and runs on
 * the app scope; every state change is confined to one serial dispatcher, so no locks are needed.
 *
 * Idle → (beginWarmUp) WarmingUp → (start, gated) Recording ⇄ paused → (stop) Saving → RunSaved → Idle.
 * Interrupted ACTIVE runs → auto-resume (background location) or NeedsRecoveryDecision.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultRecordingController(
    private val scope: CoroutineScope,
    private val clock: Clock,
    dispatchers: AppDispatchers,
    private val repository: RunRepository,
    private val settings: SettingsRepository,
    private val engines: LocationEngineManager,
    private val stepSource: StepSource,
    private val pressureSource: PressureSource,
    private val liveMetricsFactory: LiveMetricsFactory,
    private val finalizer: RunFinalizer,
    private val platform: RecordingPlatform,
    private val recoveryStore: RecoveryStore,
    private val config: ControllerConfig = ControllerConfig(),
) : RecordingController {

    private val serial = dispatchers.default.limitedParallelism(1)

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RecordingEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<RecordingEvent> = _events.asSharedFlow()

    private val _liveRoute = MutableStateFlow<MapRoute?>(null)
    override val liveRoute: StateFlow<MapRoute?> = _liveRoute.asStateFlow()

    private val _userLocation = MutableStateFlow<GeoPoint?>(null)

    /** Last primary fix position (warm-up and recording), for the live map's user dot. */
    val userLocation: StateFlow<GeoPoint?> = _userLocation.asStateFlow()

    private val gate = GpsGate()
    private var session: RecordingSession? = null
    private var warmingUp = false
    private var engineMode = LocationEngineMode.AUTO
    private var dualEngine = false
    private var locationJob: Job? = null
    private var locationKey: Pair<LocationEngineMode, Boolean>? = null
    private var sensorJob: Job? = null
    private var tickerJob: Job? = null

    private fun launchSerial(block: suspend () -> Unit) {
        scope.launch(serial) { block() }
    }

    // ---------------------------------------------------------------- public API

    override fun beginWarmUp() = launchSerial { doBeginWarmUp() }
    override fun endWarmUp() = launchSerial { doEndWarmUp() }
    override fun start() = launchSerial { doStart() }
    override fun pause() = launchSerial { session?.let { it.pause(clock.nowMillis()); publish() } }
    override fun resume() = launchSerial { session?.let { it.resume(clock.nowMillis()); publish() } }
    override fun stop() = launchSerial { doStop() }

    override suspend fun checkForInterruptedRun() {
        withContext(serial) { recover(RecoveryOrigin.APP_OPEN) }
    }

    override fun resumeInterrupted(runId: String) = launchSerial { doResumeInterrupted(runId) }
    override fun finishInterrupted(runId: String) = launchSerial { doFinishInterrupted(runId) }

    /** Sticky restart of [RecordingService] after a process kill. True when recording resumed. */
    suspend fun recoverFromService(): Boolean = withContext(serial) { recover(RecoveryOrigin.SERVICE_RESTART) }

    /**
     * True while a run is being recorded or saved. Runs on the serial queue, so it answers after every
     * command issued before it (e.g. a notification action) has been applied.
     */
    suspend fun hasActiveRun(): Boolean = withContext(serial) {
        val s = _state.value
        session != null || s is RecordingState.Recording || s is RecordingState.Saving
    }

    /** The service could not go foreground (Android refused): fall back to asking on the next app open. */
    fun onServiceStartRefused() = launchSerial {
        if (session != null) {
            _events.emit(RecordingEvent.Failure(platform.text(RecordingMessage.SERVICE_REFUSED)))
        } else if (_state.value !is RecordingState.NeedsRecoveryDecision) {
            repository.findActiveRun()?.let { askUser(it, alert = true) }
        }
    }

    // ---------------------------------------------------------------- warm-up and start

    private suspend fun doBeginWarmUp() {
        if (_state.value != RecordingState.Idle) return
        val s = settings.current()
        startLocation(s.engineMode, s.devModeUnlocked && s.devDualEngine)
        gate.reset(clock.elapsedRealtimeNanos())
        warmingUp = true
        publish()
        ensureTicker()
    }

    private fun doEndWarmUp() {
        if (_state.value !is RecordingState.WarmingUp) return
        warmingUp = false
        stopLocation()
        stopTicker()
        _state.value = RecordingState.Idle
    }

    private suspend fun doStart() {
        if (_state.value !is RecordingState.WarmingUp) return
        if (!gate.status(clock.elapsedRealtimeNanos()).startAllowed) return
        val startedAt = clock.nowMillis()
        val runId = newRunId()
        val profile = settings.current().profile
        repository.createActiveRun(
            id = runId,
            startedAtMillis = startedAt,
            zoneId = clock.zoneId().id,
            engineMode = engineMode,
            startEngine = gate.engine ?: engines.preferredEngine(engineMode),
            devDualEngine = dualEngine,
        )
        recoveryStore.clear()
        warmingUp = false
        _liveRoute.value = null
        session = RecordingSession(
            runId = runId,
            startedAtMillis = startedAt,
            dualEngine = dualEngine,
            stepsAreDetectorEvents = stepSource.hasStepDetector,
            metrics = liveMetricsFactory.create(profile, startedAt),
            repository = repository,
            hasStepCounter = stepSource.hasStepCounter,
        )
        startSensors()
        ensureTicker()
        publish()
        if (!platform.startRecordingService()) {
            _events.emit(RecordingEvent.Failure(platform.text(RecordingMessage.SERVICE_REFUSED)))
        }
    }

    // ---------------------------------------------------------------- stop

    private suspend fun doStop() {
        val s = session ?: return
        val endedAt = clock.nowMillis()
        session = null
        stopSensors()
        stopLocation()
        stopTicker()
        _state.value = RecordingState.Saving(s.runId)
        runCatching { s.flush(endedAt, final = true) }
        saveAndFinish(s.runId, endedAt)
    }

    private suspend fun saveAndFinish(runId: String, endedAt: Long) {
        if (finalizer.finish(runId, endedAt)) {
            recoveryStore.clear()
            platform.cancelRecoveryAlert()
            _events.emit(RecordingEvent.RunSaved(runId))
        } else {
            _events.emit(RecordingEvent.Failure(platform.text(RecordingMessage.SAVE_FAILED)))
        }
        _liveRoute.value = null
        _state.value = RecordingState.Idle
    }

    // ---------------------------------------------------------------- recovery

    private suspend fun recover(origin: RecoveryOrigin): Boolean {
        if (session != null) return true
        val current = _state.value
        if (current is RecordingState.Saving || current is RecordingState.NeedsRecoveryDecision) return false
        val run = repository.findActiveRun() ?: return false
        val decision = RecoveryPolicy.decide(platform.hasBackgroundLocation(), recoveryStore.autoResumeAttempts(run.id))
        if (decision == RecoveryDecision.AUTO_RESUME && isRecentInterruption(run)) {
            // From the service restart the service itself is already running.
            val serviceRunning = origin == RecoveryOrigin.SERVICE_RESTART || platform.startRecordingService()
            if (serviceRunning) {
                recoveryStore.recordAutoResume(run.id)
                resumeRun(run, userConfirmed = false)
                return true
            }
        }
        askUser(run, alert = origin == RecoveryOrigin.SERVICE_RESTART)
        return false
    }

    private suspend fun isRecentInterruption(run: Run): Boolean {
        val input = repository.loadAnalysisInput(run.id, OPEN_END_MILLIS, settings.current().profile)
        return clock.nowMillis() - lastRecordedMillis(input) <= config.autoResumeMaxGapMillis
    }

    private suspend fun askUser(run: Run, alert: Boolean) {
        cancelWarmUp()
        val (input, metrics) = replay(run)
        val distance = metrics.snapshot(lastRecordedMillis(input)).distanceM
        _state.value = RecordingState.NeedsRecoveryDecision(run.id, run.startedAtMillis, distance)
        if (alert) platform.postRecoveryAlert()
    }

    private suspend fun doResumeInterrupted(runId: String) {
        val current = _state.value as? RecordingState.NeedsRecoveryDecision ?: return
        if (current.runId != runId) return
        platform.cancelRecoveryAlert()
        val run = repository.getRun(runId)
        if (run == null) {
            _state.value = RecordingState.Idle
            return
        }
        recoveryStore.clear()
        resumeRun(run, userConfirmed = true)
        if (!platform.startRecordingService()) {
            _events.emit(RecordingEvent.Failure(platform.text(RecordingMessage.SERVICE_REFUSED)))
        }
    }

    private suspend fun doFinishInterrupted(runId: String) {
        val current = _state.value as? RecordingState.NeedsRecoveryDecision ?: return
        if (current.runId != runId) return
        platform.cancelRecoveryAlert()
        _state.value = RecordingState.Saving(runId)
        val profile = settings.current().profile
        // End at the last recorded data, not now: the interruption gap is not part of the run.
        val endedAt = lastRecordedMillis(repository.loadAnalysisInput(runId, OPEN_END_MILLIS, profile))
        saveAndFinish(runId, endedAt)
    }

    private suspend fun replay(run: Run): Pair<AnalysisInput, LiveMetrics> {
        val profile = settings.current().profile
        val input = repository.loadAnalysisInput(run.id, OPEN_END_MILLIS, profile)
        val metrics = liveMetricsFactory.create(profile, run.startedAtMillis)
        replayInto(metrics, input)
        return input to metrics
    }

    /** Rebuilds the session from the database and restarts location + sensors. */
    private suspend fun resumeRun(run: Run, userConfirmed: Boolean) {
        cancelWarmUp()
        val profile = settings.current().profile
        val input = repository.loadAnalysisInput(run.id, OPEN_END_MILLIS, profile)
        val metrics = liveMetricsFactory.create(profile, run.startedAtMillis)
        val paused = replayInto(metrics, input)
        val lastRecorded = lastRecordedMillis(input)
        val now = clock.nowMillis()
        val gapAsPause = userConfirmed && !paused && now - lastRecorded >= config.manualResumeGapPauseMillis
        if (gapAsPause) {
            // Nothing was recorded during the gap and the user may have stopped long ago: keep it out of moving time.
            repository.startPause(run.id, lastRecorded)
            repository.endPause(run.id, now)
            metrics.onPauseChanged(true, lastRecorded)
            metrics.onPauseChanged(false, now)
        }
        // Otherwise the gap is bridged (step counter distance, else straight line): see RecordingSession.
        val resumeGap = if (paused || gapAsPause) null
        else resumeGapAnchor(input, now, bootedAtMillis = now - clock.elapsedRealtimeNanos() / 1_000_000)
        session = RecordingSession(
            runId = run.id,
            startedAtMillis = run.startedAtMillis,
            dualEngine = run.devDualEngine,
            stepsAreDetectorEvents = stepSource.hasStepDetector,
            metrics = metrics,
            repository = repository,
            initiallyPaused = paused,
            initialStepCount = input.sensorSamples.filter { it.type == SensorType.STEP_COUNTER }.maxOfOrNull { it.value } ?: 0.0,
            // A bridged gap ends at the resume: fixes cached from the dead time would land inside it.
            notBeforeMillis = if (resumeGap != null) now else lastRecorded,
            hasStepCounter = stepSource.hasStepCounter,
            resumeGap = resumeGap,
        )
        _liveRoute.value = metrics.acceptedRoute()
        startLocation(run.engineMode, run.devDualEngine)
        gate.reset(clock.elapsedRealtimeNanos())
        startSensors()
        ensureTicker()
        publish()
    }

    private fun cancelWarmUp() {
        if (!warmingUp) return
        warmingUp = false
        stopLocation()
        stopTicker()
    }

    // ---------------------------------------------------------------- location, sensors, ticker

    private fun startLocation(mode: LocationEngineMode, dual: Boolean) {
        engineMode = mode
        dualEngine = dual
        val key = mode to dual
        if (locationJob?.isActive == true && locationKey == key) return
        locationJob?.cancel()
        locationKey = key
        val preferred = engines.preferredEngine(mode)
        gate.onEngine(preferred, fallbackActive = mode == LocationEngineMode.AUTO && preferred == LocationEngine.PLATFORM)
        locationJob = scope.launch(serial) {
            engines.stream(mode, dual).catch { }.collect { onEngineEvent(it) }
        }
    }

    private fun stopLocation() {
        locationJob?.cancel()
        locationJob = null
        locationKey = null
    }

    private suspend fun onEngineEvent(event: EngineEvent) {
        when (event) {
            is EngineEvent.Fix -> {
                if (event.isPrimary) {
                    gate.onPrimaryFix(event.fix, clock.elapsedRealtimeNanos())
                    _userLocation.value = GeoPoint(event.fix.lat, event.fix.lon)
                }
                session?.onFix(event.fix, event.isPrimary)
            }
            is EngineEvent.Switched -> {
                gate.onEngine(event.to, event.fallbackActive)
                session?.let { repository.markEngineFallback(it.runId) }
                _events.emit(RecordingEvent.EngineSwitched(event.from, event.to))
            }
            is EngineEvent.Unavailable -> if (event.engine == LocationEngine.PLAY) {
                _events.emit(RecordingEvent.Failure(platform.text(RecordingMessage.PLAY_UNAVAILABLE)))
            }
        }
    }

    private fun startSensors() {
        sensorJob?.cancel()
        sensorJob = scope.launch(serial) {
            launch { stepSource.steps().catch { }.collect { session?.onSteps(it) } }
            if (stepSource.hasStepCounter) launch { stepSource.counterReadings().catch { }.collect { session?.onCounterReading(it) } }
            if (pressureSource.isAvailable) launch { pressureSource.pressure().catch { }.collect { session?.onPressure(it) } }
        }
    }

    private fun stopSensors() {
        sensorJob?.cancel()
        sensorJob = null
    }

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch(serial) {
            while (isActive) {
                tick()
                delay(config.tickMillis)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private suspend fun tick() {
        val s = session
        if (s != null) {
            val now = clock.nowMillis()
            if (now - s.lastFlushMillis >= config.flushMillis) runCatching { s.flush(now) }
        }
        publish()
    }

    /** Pushes WarmingUp/Recording state (other states are set explicitly). */
    private fun publish() {
        val nanos = clock.elapsedRealtimeNanos()
        val s = session
        when {
            s != null -> {
                _state.value = RecordingState.Recording(
                    runId = s.runId,
                    startedAtMillis = s.startedAtMillis,
                    paused = s.paused,
                    stats = s.snapshot(clock.nowMillis()),
                    gps = recordingGps(gate.status(nanos)),
                )
                s.routeIfChanged()?.let { _liveRoute.value = it }
            }
            // waitingMillis grows every tick but nothing shows it since Start stopped waiting for GPS
            // (b7a33c1): zeroed, so the ticker republishes only when the GPS status itself changes.
            warmingUp -> _state.value = RecordingState.WarmingUp(gate.status(nanos).copy(waitingMillis = 0))
        }
    }

    private fun recordingGps(status: GpsStatus): GpsStatus = status.copy(
        startAllowed = true,
        weakSignalWarning = status.quality == GpsQuality.POOR || status.quality == GpsQuality.NONE,
        waitingMillis = 0,
    )
}
