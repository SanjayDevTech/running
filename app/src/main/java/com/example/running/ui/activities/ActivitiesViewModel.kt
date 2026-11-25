package com.example.running.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.MapRoute
import com.example.running.core.util.AppDispatchers
import com.example.running.core.util.Clock
import com.example.running.core.util.LruMap
import com.example.running.data.repo.RunRepository
import com.example.running.map.ThumbnailRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

internal class ActivitiesViewModel(
    private val repository: RunRepository,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    thumbnailCacheSize: Int = THUMBNAIL_CACHE_SIZE,
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter.ALL)

    /** Main thread only. Thin thumbnails (a few KB each), bounded: the tab's ViewModel outlives many scrolls. */
    private val thumbnails = LruMap<String, ThumbnailRoute?>(thumbnailCacheSize)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ActivitiesUiState> = filter
        .flatMapLatest { f -> repository.observeRuns(f).map { runs -> buildActivitiesState(f, runs, clock) } }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivitiesUiState())

    fun setFilter(value: ActivityFilter) {
        filter.value = value
    }

    /** Number of cached thumbnails (tests). */
    internal val cachedThumbnails: Int get() = thumbnails.size

    /**
     * Thumbnail for a card, from the full route (pause edges and pace colours match Run detail), built
     * off the main thread and cached per run id. Call from the main thread.
     */
    suspend fun thumbnailFor(runId: String): ThumbnailRoute? {
        if (thumbnails.containsKey(runId)) return thumbnails[runId]
        val thumbnail = withContext(dispatchers.default) {
            ThumbnailRoute.of(MapRoute.fromTrackPoints(repository.getFilteredPoints(runId)))
        }
        thumbnails[runId] = thumbnail
        return thumbnail
    }

    private companion object {
        const val THUMBNAIL_CACHE_SIZE = 100
    }
}
