package com.example.running.di

import com.example.running.RunningApp
import com.example.running.analysis.AnalysisModule
import com.example.running.core.util.DefaultAppDispatchers
import com.example.running.core.util.RealClock
import com.example.running.data.db.RunningDatabase
import com.example.running.data.repo.RoomRunRepository
import com.example.running.data.repo.RunRepository
import com.example.running.data.settings.DataStoreSettingsRepository
import com.example.running.data.settings.SettingsRepository
import com.example.running.map.MapModule
import com.example.running.recording.RecordingModule
import com.example.running.setup.SetupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Manual DI root, one per process (RunningApp.container). */
class AppContainer(app: RunningApp) {
    val core: CoreDeps
    val analysis: AnalysisModule
    val map: MapModule
    val recording: RecordingModule
    val setup: SetupModule

    init {
        val clock = RealClock
        val dispatchers = DefaultAppDispatchers
        val database = RunningDatabase.build(app)
        // The namer is a provider so the repository can exist before AnalysisModule.
        val repository = RoomRunRepository(database, clock, dispatchers) { analysis.runNamer }
        core = CoreDeps(
            app = app,
            clock = clock,
            dispatchers = dispatchers,
            appScope = CoroutineScope(SupervisorJob() + dispatchers.default),
            database = database,
            runRepository = repository,
            settings = DataStoreSettingsRepository(app),
        )
        analysis = AnalysisModule(core.clock)                     // B
        map = MapModule(core)                                     // C
        recording = RecordingModule(core, analysis, map)          // A
        setup = SetupModule(core)                                 // F
    }

    // Convenience pass-throughs used by screens.
    val runRepository: RunRepository get() = core.runRepository
    val settings: SettingsRepository get() = core.settings
}
