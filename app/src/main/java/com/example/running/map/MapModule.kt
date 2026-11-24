package com.example.running.map

import android.app.Application
import com.example.running.core.contracts.MapOfflineManager
import com.example.running.di.CoreDeps

/** CONTRACT(C). Public signature frozen (architecture.md §3.4). */
class MapModule(core: CoreDeps) {
    private val app: Application = core.app

    val offline: MapOfflineManager = RegionOfflineManager(MapLibreOfflineBackend(core.app, core.dispatchers))

    /** Main thread, from RunningApp.onCreate: MapLibre.getInstance + ambient cache size + style file. */
    fun initialize() = MapRuntime.initialize(app)
}
