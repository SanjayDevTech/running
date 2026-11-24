package com.example.running.recording

import com.example.running.analysis.AnalysisModule
import com.example.running.core.contracts.RecordingController
import com.example.running.di.CoreDeps
import com.example.running.location.LocationEngineManager
import com.example.running.location.PlatformLocationSource
import com.example.running.location.PlayLocationSource
import com.example.running.map.MapModule
import com.example.running.sensors.AndroidPressureSource
import com.example.running.sensors.AndroidStepSource

/** CONTRACT(A). Public signature frozen (architecture.md §3.1). */
class RecordingModule(core: CoreDeps, analysis: AnalysisModule, map: MapModule) {
    internal val clock = core.clock
    internal val notifications = RecordingNotifications(core.app)

    internal val controllerImpl = DefaultRecordingController(
        scope = core.appScope,
        clock = core.clock,
        dispatchers = core.dispatchers,
        repository = core.runRepository,
        settings = core.settings,
        engines = LocationEngineManager(
            sources = listOf(PlayLocationSource(core.app), PlatformLocationSource(core.app)),
            clock = core.clock,
        ),
        stepSource = AndroidStepSource(core.app, core.clock),
        pressureSource = AndroidPressureSource(core.app, core.clock),
        liveMetricsFactory = analysis.liveMetricsFactory,
        finalizer = RunFinalizer(
            repository = core.runRepository,
            settings = core.settings,
            analyzer = analysis.runAnalyzer,
            offline = map.offline,
            appScope = core.appScope,
            dispatchers = core.dispatchers,
        ),
        platform = AndroidRecordingPlatform(core.app, notifications),
        recoveryStore = SharedPrefsRecoveryStore(core.app),
    )

    val controller: RecordingController = controllerImpl
}
