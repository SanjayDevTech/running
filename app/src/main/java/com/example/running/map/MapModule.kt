package com.example.running.map

import android.app.Application
import com.example.running.core.contracts.MapOfflineManager
import com.example.running.di.CoreDeps

/** CONTRACT(C). Public signature frozen (architecture.md §3.4). */
class MapModule(core: CoreDeps) {
    private val app: Application = core.app

    val offline: MapOfflineManager = RegionOfflineManager(MapLibreOfflineBackend(core.app, core.dispatchers))

    /**
     * Main thread. MapLibre.getInstance + ambient cache size + style file; idempotent. Optional:
     * RouteMap and the offline manager call it themselves on first use (lazy since §10.10).
     */
    fun initialize() = MapRuntime.initialize(app)
}
