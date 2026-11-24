package com.example.running.testing

import android.app.PendingIntent
import com.example.running.core.contracts.AccuracyState
import com.example.running.core.contracts.LocationAccuracyChecker
import com.example.running.core.contracts.MapOfflineManager
import com.example.running.core.contracts.RunAnalyzer
import com.example.running.core.model.ActivityType
import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.OfflineAreaStatus
import com.example.running.core.model.PointKind
import com.example.running.core.model.RunAnalysis
import com.example.running.core.model.RunStats

class FakeMapOfflineManager(var downloadResult: Boolean = true) : MapOfflineManager {
    val statuses = mutableMapOf<String, OfflineAreaStatus>()
    val downloads = mutableListOf<Pair<String, GeoBounds>>()
    val deleted = mutableListOf<String>()

    override suspend fun downloadRunArea(runId: String, bounds: GeoBounds): Boolean {
        downloads += runId to bounds
        statuses[runId] = if (downloadResult) OfflineAreaStatus.READY else OfflineAreaStatus.FAILED
        return downloadResult
    }

    override suspend fun deleteRunArea(runId: String) {
        deleted += runId
        statuses.remove(runId)
    }

    override suspend fun status(runId: String): OfflineAreaStatus = statuses[runId] ?: OfflineAreaStatus.NONE
}

class FakeAccuracyChecker(var state: AccuracyState = AccuracyState.ON) : LocationAccuracyChecker {
    override suspend fun check(): AccuracyState = state
    override suspend fun resolution(): PendingIntent? = null
}

/** Records inputs and returns [result] (default: raw points copied as FILTERED, zero stats). */
class FakeRunAnalyzer(var result: ((AnalysisInput) -> RunAnalysis)? = null) : RunAnalyzer {
    val inputs = mutableListOf<AnalysisInput>()

    override fun analyze(input: AnalysisInput): RunAnalysis {
        inputs += input
        return result?.invoke(input) ?: RunAnalysis(
            stats = RunStats(elapsedMillis = input.endedAtMillis - input.startedAtMillis),
            suggestedType = input.manualType ?: ActivityType.RUN,
            suggestedName = "Test Run",
            filteredPoints = input.rawPoints.map { it.copy(kind = PointKind.FILTERED) },
            splits = emptyList(),
            segments = emptyList(),
            bestEfforts = emptyList(),
            learnedRunStrideM = null,
            learnedWalkStrideM = null,
            analysisVersion = 1,
        )
    }
}
