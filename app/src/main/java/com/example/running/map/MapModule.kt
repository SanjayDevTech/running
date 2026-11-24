package com.example.running.map

import com.example.running.core.contracts.MapOfflineManager
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.OfflineAreaStatus
import com.example.running.di.CoreDeps

/** CONTRACT(C). Public signature frozen (architecture.md §3.4). */
class MapModule(core: CoreDeps) {
    val offline: MapOfflineManager = StubMapOfflineManager()

    /** Main thread, from RunningApp.onCreate: MapLibre.getInstance + ambient cache size. Stub: no-op. */
    fun initialize() = Unit
}

private class StubMapOfflineManager : MapOfflineManager {
    override suspend fun downloadRunArea(runId: String, bounds: GeoBounds): Boolean = false
    override suspend fun deleteRunArea(runId: String) = Unit
    override suspend fun status(runId: String): OfflineAreaStatus = OfflineAreaStatus.NONE
}
