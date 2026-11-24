package com.example.running.ui.rundetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.running.core.contracts.MapOfflineManager
import com.example.running.core.contracts.RunSeriesBuilder
import com.example.running.core.model.ActivityType
import com.example.running.core.model.MapRoute
import com.example.running.core.model.OfflineAreaStatus
import com.example.running.core.util.AppDispatchers
import com.example.running.data.repo.RunRepository
import com.example.running.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RunDetailViewModel(
    private val runId: String,
    private val repository: RunRepository,
    settings: SettingsRepository,
    private val seriesBuilder: RunSeriesBuilder,
    private val offline: MapOfflineManager,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val deleting = MutableStateFlow(false)

    /** Reloaded only when the analysis result changes, not on rename / type change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val geometry = repository.observeRun(runId)
        .map { run -> run?.let { it.analysisVersion to it.endedAtMillis } }
        .distinctUntilChanged()
        .mapLatest { key -> if (key == null) RunGeometry.EMPTY else loadGeometry() }

    private suspend fun loadGeometry(): RunGeometry = withContext(dispatchers.default) {
        val points = repository.getFilteredPoints(runId)
        if (points.size < 2) return@withContext RunGeometry.EMPTY
        val series = seriesBuilder.build(points)
        val route = MapRoute.fromTrackPoints(points)
        retryOfflineArea(route)
        RunGeometry(
            route = route,
            pace = paceChartPoints(series.pace),
            elevation = elevationChartPoints(series.elevation),
        )
    }

    private val parts = combine(
        repository.observeSplits(runId),
        repository.observeSegments(runId),
        repository.observeBestEfforts(runId),
        settings.settings.map { it.devModeUnlocked }.distinctUntilChanged(),
    ) { splits, segments, efforts, dev -> Parts(splits, segments, efforts, dev) }

    private class Parts(
        val splits: List<com.example.running.core.model.Split>,
        val segments: List<com.example.running.core.model.Segment>,
        val efforts: List<com.example.running.core.model.BestEffortWithRank>,
        val dev: Boolean,
    )

    val state: StateFlow<RunDetailUiState> = combine(
        repository.observeRun(runId), geometry, parts, deleting,
    ) { run, geo, p, isDeleting ->
        when {
            isDeleting -> RunDetailUiState.Loading
            run == null -> RunDetailUiState.NotFound
            else -> RunDetailUiState.Content(
                buildRunDetailContent(run, geo, p.splits, p.segments, p.efforts, p.dev),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunDetailUiState.Loading)

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.rename(runId, trimmed) }
    }

    fun setType(type: ActivityType) {
        viewModelScope.launch { repository.setType(runId, type) }
    }

    /**
     * The finalizer's download is fire-and-forget and fails when the phone is offline at the end of
     * a run, so opening the run retries it. Tied to this screen: leaving cancels it and the region
     * reports FAILED, which the next open retries again.
     */
    private fun retryOfflineArea(route: MapRoute) {
        val bounds = route.bounds ?: return
        viewModelScope.launch {
            val status = offline.status(runId)
            if ((status == OfflineAreaStatus.NONE || status == OfflineAreaStatus.FAILED) && !deleting.value) {
                offline.downloadRunArea(runId, bounds)
            }
        }
    }

    /** deleteRun -> deleteRunArea -> [onDone] (architecture.md §3.8). */
    fun delete(onDone: () -> Unit) {
        deleting.value = true
        viewModelScope.launch {
            repository.deleteRun(runId)
            offline.deleteRunArea(runId)
            onDone()
        }
    }
}
