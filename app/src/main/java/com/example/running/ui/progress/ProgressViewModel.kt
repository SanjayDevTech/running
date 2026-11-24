package com.example.running.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.Run
import com.example.running.core.util.AppDispatchers
import com.example.running.core.util.Clock
import com.example.running.data.repo.RunRepository
import com.example.running.ui.activities.RunCardUi
import com.example.running.ui.activities.toCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private data class Selection(
    val filter: ActivityFilter = ActivityFilter.ALL,
    val period: ProgressPeriod = ProgressPeriod.WEEKLY,
    val metric: ProgressMetric = ProgressMetric.DISTANCE,
    /** null = the current (last) bucket. */
    val selectedIndex: Int? = null,
)

internal class ProgressViewModel(
    repository: RunRepository,
    private val clock: Clock,
    dispatchers: AppDispatchers,
) : ViewModel() {
    private val selection = MutableStateFlow(Selection())

    val zone get() = clock.zoneId()

    /** The same row model as an Activities card, so "Recent" reads like the Activities list. */
    fun card(run: Run): RunCardUi = run.toCard(clock)

    /** null until the first emission from the database. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ProgressUiState?> = selection
        .map { it.filter }
        .distinctUntilChanged()
        .flatMapLatest { repository.observeRuns(it) }
        .combine(selection) { runs, s ->
            buildProgressState(runs, s.filter, s.period, s.metric, s.selectedIndex, clock.nowMillis(), clock.zoneId())
        }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFilter(filter: ActivityFilter) = selection.update { it.copy(filter = filter, selectedIndex = null) }
    fun setPeriod(period: ProgressPeriod) = selection.update { it.copy(period = period, selectedIndex = null) }
    fun setMetric(metric: ProgressMetric) = selection.update { it.copy(metric = metric) }
    fun selectBar(index: Int) = selection.update { it.copy(selectedIndex = index) }
}

internal data class PersonalBestsUiState(
    val filter: ActivityFilter,
    val rows: List<PbRow>,
) {
    val isEmpty: Boolean get() = rows.all { it.runId == null }
}

internal class PersonalBestsViewModel(
    repository: RunRepository,
    private val clock: Clock,
    dispatchers: AppDispatchers,
) : ViewModel() {
    private val filter = MutableStateFlow(ActivityFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PersonalBestsUiState?> = filter
        .flatMapLatest { f -> repository.observePersonalBests(f).map { f to it } }
        .map { (f, pb) -> PersonalBestsUiState(f, buildPbRows(pb, clock.zoneId().id)) }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFilter(f: ActivityFilter) {
        filter.value = f
    }
}
