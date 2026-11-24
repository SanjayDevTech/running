package com.example.running.core.contracts

import com.example.running.core.model.GeoBounds
import com.example.running.core.model.OfflineAreaStatus

/** Implemented by track C (map/). */
interface MapOfflineManager {
    suspend fun downloadRunArea(runId: String, bounds: GeoBounds): Boolean
    suspend fun deleteRunArea(runId: String)
    suspend fun status(runId: String): OfflineAreaStatus
}
