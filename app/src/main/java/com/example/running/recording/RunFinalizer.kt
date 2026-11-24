package com.example.running.recording

import com.example.running.core.contracts.MapOfflineManager
import com.example.running.core.contracts.RunAnalyzer
import com.example.running.core.util.AppDispatchers
import com.example.running.data.repo.RunRepository
import com.example.running.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stop-and-save pipeline (architecture.md §3.1): close the open pause → load the analysis input →
 * analyse (B) → save → learned strides → offline map area (fire-and-forget, C).
 */
internal class RunFinalizer(
    private val repository: RunRepository,
    private val settings: SettingsRepository,
    private val analyzer: RunAnalyzer,
    private val offline: MapOfflineManager,
    private val appScope: CoroutineScope,
    private val dispatchers: AppDispatchers,
) {
    /** True when the run was saved as FINISHED; false leaves it ACTIVE (offered again by recovery). */
    suspend fun finish(runId: String, endedAtMillis: Long): Boolean = try {
        repository.endPause(runId, endedAtMillis)
        val profile = settings.current().profile
        val input = repository.loadAnalysisInput(runId, endedAtMillis, profile)
        val analysis = withContext(dispatchers.default) { analyzer.analyze(input) }
        repository.saveAnalysis(runId, endedAtMillis, analysis)
        if (analysis.learnedRunStrideM != null || analysis.learnedWalkStrideM != null) {
            settings.setLearnedStrides(analysis.learnedRunStrideM, analysis.learnedWalkStrideM)
        }
        analysis.stats.bounds?.let { bounds ->
            appScope.launch { runCatching { offline.downloadRunArea(runId, bounds) } }
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
