package com.example.running.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.MapRoute
import com.example.running.core.util.Clock
import com.example.running.data.repo.RunRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class ActivitiesViewModel(
    private val repository: RunRepository,
    private val clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter.ALL)
    private val routeCache = HashMap<String, MapRoute?>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ActivitiesUiState> = filter
        .flatMapLatest { f -> repository.observeRuns(f).map { runs -> buildActivitiesState(f, runs, clock) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivitiesUiState())

    fun setFilter(value: ActivityFilter) {
        filter.value = value
    }

    /** Thumbnail route for a card; computed once per run id (list rows scroll in and out). */
    suspend fun routeFor(runId: String): MapRoute? {
        if (routeCache.containsKey(runId)) return routeCache[runId]
        val route = thumbnailRoute(repository.getFilteredPoints(runId))
        routeCache[runId] = route
        return route
    }
}
