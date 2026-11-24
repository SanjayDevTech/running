package com.example.running.ui.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.running.R
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.MapRoute
import com.example.running.core.model.PointKind
import com.example.running.core.model.RouteColoring
import com.example.running.core.model.RouteMapMode
import com.example.running.core.util.formatDistance
import com.example.running.core.util.formatDurationShort
import com.example.running.data.repo.RunRepository
import com.example.running.di.appViewModel
import com.example.running.map.RouteMap
import com.example.running.ui.components.AppTopBar
import com.example.running.ui.components.EmptyState
import com.example.running.ui.components.SectionHeader
import com.example.running.ui.components.displayName
import com.example.running.core.util.formatMeters
import com.example.running.core.util.formatPercent
import com.example.running.core.util.formatSecondsPrecise
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.Hairline
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.TextPrimary
import com.example.running.ui.theme.TextSecondary
import com.example.running.ui.theme.Walk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class DevCompareUiState(
    val loading: Boolean = true,
    val stats: List<EngineStats> = emptyList(),
    val routes: Map<LocationEngine, MapRoute> = emptyMap(),
)

internal class DevCompareViewModel(runId: String, repository: RunRepository) : ViewModel() {
    private val _state = MutableStateFlow(DevCompareUiState())
    val state: StateFlow<DevCompareUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = repository.getRawPoints(runId).filter { it.kind == PointKind.RAW }
            val routes = raw.filter { it.engine != null }.groupBy { it.engine!! }.mapValues { (engine, pts) ->
                MapRoute.fromTrackPoints(pts.sortedBy { it.timeMillis }, RouteColoring.SOLID)
                    .copy(tintArgb = engineColor(engine).toArgb(), label = engine.displayName())
            }
            _state.value = DevCompareUiState(loading = false, stats = compareEngines(raw), routes = routes)
        }
    }
}

internal fun engineColor(engine: LocationEngine): Color = when (engine) {
    LocationEngine.PLAY -> Accent
    LocationEngine.PLATFORM -> Walk
}

/** CONTRACT(A). Public signature frozen (architecture.md §3.8). Hidden dev mode: both engines side by side. */
@Composable
fun DevEngineCompareScreen(runId: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "dev-compare-$runId") { c -> DevCompareViewModel(runId, c.runRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Black)) {
        AppTopBar(title = stringResource(R.string.dev_compare_title), onBack = onBack)
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TextPrimary)
            }
            state.stats.isEmpty() -> EmptyState(
                title = stringResource(R.string.dev_compare_empty_title),
                body = stringResource(R.string.dev_compare_empty_body),
            )
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                RouteMap(
                    route = null,
                    comparison = state.routes.values.toList(),
                    mode = RouteMapMode.INTERACTIVE,
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                )
                Row(
                    Modifier.padding(horizontal = Spacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    state.stats.forEach { Legend(it.engine) }
                }
                SectionHeader(title = stringResource(R.string.dev_compare_stats), modifier = Modifier.padding(horizontal = Spacing.gutter))
                CompareTable(state.stats)
            }
        }
    }
}

@Composable
private fun Legend(engine: LocationEngine) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(engineColor(engine)))
        Spacer(Modifier.width(Spacing.s))
        Text(engine.displayName(), style = AppText.label, color = TextPrimary)
    }
}

@Composable
private fun CompareTable(stats: List<EngineStats>) {
    val rows: List<Pair<String, (EngineStats) -> String>> = listOf(
        stringResource(R.string.dev_compare_fixes) to { s -> s.fixes.toString() },
        stringResource(R.string.dev_compare_distance) to { s -> formatDistance(s.distanceM) },
        stringResource(R.string.dev_compare_median_accuracy) to { s -> formatMeters(s.medianAccuracyM) },
        stringResource(R.string.dev_compare_p95_accuracy) to { s -> formatMeters(s.p95AccuracyM) },
        stringResource(R.string.dev_compare_interval) to { s -> formatSecondsPrecise(s.meanIntervalMillis) },
        stringResource(R.string.dev_compare_max_gap) to { s -> s.maxGapMillis?.let(::formatDurationShort) ?: "–" },
        stringResource(R.string.dev_compare_primary_share) to { s -> formatPercent(s.primaryShare) },
    )
    Column(Modifier.padding(horizontal = Spacing.gutter)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
            Spacer(Modifier.weight(1.4f))
            stats.forEach {
                Text(it.engine.displayName().uppercase(), style = AppText.eyebrow, color = TextSecondary, modifier = Modifier.weight(1f))
            }
        }
        rows.forEach { (label, value) ->
            HorizontalDivider(color = Hairline)
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = AppText.bodyS, color = TextSecondary, modifier = Modifier.weight(1.4f))
                stats.forEach { s ->
                    Text(value(s), style = AppText.numberM, color = TextPrimary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
