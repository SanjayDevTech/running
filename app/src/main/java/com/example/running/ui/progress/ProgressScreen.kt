package com.example.running.ui.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.running.R
import com.example.running.core.util.formatDistanceKm
import com.example.running.core.util.formatDurationShort
import com.example.running.core.util.formatElevation
import com.example.running.di.appViewModel
import com.example.running.ui.activities.RunRow
import com.example.running.ui.components.ActivityFilterChips
import com.example.running.ui.components.AppCard
import com.example.running.ui.components.ChoiceChips
import com.example.running.ui.components.EmptyState
import com.example.running.ui.components.ScreenTitle
import com.example.running.ui.components.SectionHeader
import com.example.running.ui.components.StatItem
import com.example.running.ui.components.StatRow
import com.example.running.ui.components.StatSize
import com.example.running.ui.components.charts.AppBarChart
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.Motion
import com.example.running.ui.theme.listItemMotion
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.TextTertiary

/** CONTRACT(E). Public signature frozen (architecture.md §3.8). */
@Composable
fun ProgressScreen(onOpenPersonalBests: () -> Unit, onOpenRun: (runId: String) -> Unit) {
    val vm = appViewModel { c -> ProgressViewModel(c.runRepository, c.core.clock, c.core.dispatchers) }
    val state by vm.state.collectAsState()
    val s = state
    Column(Modifier.fillMaxSize().background(Black)) {
        ScreenTitle(stringResource(R.string.progress_title))
        if (s == null) return@Column
        // A filter with no runs swaps the whole body for the empty state: fade through, like a tab.
        AnimatedContent(s.isEmpty, transitionSpec = { Motion.fadeThrough() }, label = "progressBody") { empty ->
            if (empty) {
                Column(Modifier.fillMaxSize()) {
                    ActivityFilterChips(
                        selected = s.filter, onSelect = vm::setFilter,
                        modifier = Modifier.padding(horizontal = Spacing.gutter),
                    )
                    EmptyState(
                        title = stringResource(R.string.progress_empty_title),
                        body = stringResource(R.string.progress_empty_body),
                        modifier = Modifier.weight(1f),
                    )
                }
                return@AnimatedContent
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        ActivityFilterChips(selected = s.filter, onSelect = vm::setFilter)
                        val weekly = stringResource(R.string.progress_period_weekly)
                        val monthly = stringResource(R.string.progress_period_monthly)
                        ChoiceChips(
                            options = ProgressPeriod.entries, selected = s.period, onSelect = vm::setPeriod,
                            label = { if (it == ProgressPeriod.WEEKLY) weekly else monthly },
                        )
                        val metricLabels = ProgressMetric.entries.associateWith { metricLabel(it) }
                        ChoiceChips(
                            options = ProgressMetric.entries, selected = s.metric, onSelect = vm::setMetric,
                            label = { metricLabels.getValue(it) },
                        )
                    }
                }
                item { SelectedPeriod(s, vm.zone) }
                item {
                    AppBarChart(
                        bars = s.bars,
                        yFormatter = { s.metric.formatAxis(it, s.axisMax) },
                        selectedIndex = s.selectedIndex,
                        onSelect = vm::selectBar,
                        yStep = s.metric.axisStep(s.axisMax),
                    )
                }
                item { SectionHeader(stringResource(R.string.progress_streaks)) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        val wk = stringResource(R.string.progress_unit_weeks)
                        val d = stringResource(R.string.progress_unit_days)
                        StatRow(
                            listOf(
                                StatItem(stringResource(R.string.progress_streak_current_weeks), s.streaks.currentWeeks.toString(), wk),
                                StatItem(stringResource(R.string.progress_streak_longest_weeks), s.streaks.longestWeeks.toString(), wk),
                            ),
                            animateValues = true,
                        )
                        StatRow(
                            listOf(
                                StatItem(stringResource(R.string.progress_streak_current_days), s.streaks.currentDays.toString(), d),
                                StatItem(stringResource(R.string.progress_streak_longest_days), s.streaks.longestDays.toString(), d),
                            ),
                            animateValues = true,
                        )
                    }
                }
                item { SectionHeader(stringResource(R.string.progress_totals)) }
                item {
                    StatRow(
                        listOf(
                            StatItem(stringResource(R.string.progress_metric_runs), s.allTime.count.toString()),
                            StatItem(stringResource(R.string.stat_distance), formatDistanceKm(s.allTime.distanceM, 0), stringResource(R.string.unit_km)),
                            StatItem(stringResource(R.string.stat_time), formatDurationShort(s.allTime.movingMillis)),
                        ),
                        size = StatSize.M,
                        animateValues = true,
                    )
                }
                item { PersonalBestsLink(onOpenPersonalBests) }
                item { SectionHeader(stringResource(R.string.progress_recent)) }
                items(s.recent, key = { it.id }) { run ->
                    val card = remember(run) { vm.card(run) }
                    RunRow(card, onClick = { onOpenRun(run.id) }, modifier = listItemMotion())
                }
            }
        }
    }
}

@Composable
private fun metricLabel(m: ProgressMetric): String = when (m) {
    ProgressMetric.DISTANCE -> stringResource(R.string.stat_distance)
    ProgressMetric.TIME -> stringResource(R.string.stat_time)
    ProgressMetric.RUNS -> stringResource(R.string.progress_metric_runs)
    ProgressMetric.ELEVATION -> stringResource(R.string.stat_elevation)
}

@Composable
private fun SelectedPeriod(s: ProgressUiState, zone: java.time.ZoneId) {
    val caption = when (val c = selectedCaption(s.selected, s.period, s.isCurrentSelected, zone)) {
        Caption.ThisWeek -> stringResource(R.string.progress_this_week)
        Caption.ThisMonth -> stringResource(R.string.progress_this_month)
        is Caption.WeekOf -> stringResource(R.string.progress_week_of, c.date)
        is Caption.Month -> c.label
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // Picking another bar: the caption crossfades (gym's period pager), the figures below too.
        AnimatedContent(caption, transitionSpec = { Motion.crossfade() }, label = "periodCaption") { text ->
            Text(text, style = AppText.titleM, color = MaterialTheme.colorScheme.onSurface)
        }
        StatRow(
            listOf(
                StatItem(stringResource(R.string.stat_distance), formatDistanceKm(s.selected.distanceM, 1), stringResource(R.string.unit_km)),
                StatItem(stringResource(R.string.stat_time), formatDurationShort(s.selected.movingMillis)),
                StatItem(stringResource(R.string.progress_metric_runs), s.selected.count.toString()),
                StatItem(stringResource(R.string.stat_elevation), formatElevation(s.selected.elevationGainM)),
            ),
            size = StatSize.M,
            animateValues = true,
        )
    }
}

@Composable
private fun PersonalBestsLink(onClick: () -> Unit) = AppCard(onClick = onClick) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.progress_pb_link), style = AppText.titleM, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.progress_pb_link_subtitle), style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextTertiary)
    }
}
