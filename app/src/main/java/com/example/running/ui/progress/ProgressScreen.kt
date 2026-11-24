package com.example.running.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.running.R
import com.example.running.core.model.Run
import com.example.running.core.util.formatDistanceKm
import com.example.running.core.util.formatDurationShort
import com.example.running.core.util.formatElevation
import com.example.running.core.util.formatRunDate
import com.example.running.di.appViewModel
import com.example.running.ui.components.ActivityFilterChips
import com.example.running.ui.components.ActivityTypeIcon
import com.example.running.ui.components.AppTopBar
import com.example.running.ui.components.ChoiceChips
import com.example.running.ui.components.EmptyState
import com.example.running.ui.components.SectionHeader
import com.example.running.ui.components.StatItem
import com.example.running.ui.components.StatRow
import com.example.running.ui.components.StatSize
import com.example.running.ui.components.charts.AppBarChart
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.Radius
import com.example.running.ui.theme.Raised1
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.TextPrimary
import com.example.running.ui.theme.TextSecondary
import com.example.running.ui.theme.TextTertiary

/** CONTRACT(E). Public signature frozen (architecture.md §3.8). */
@Composable
fun ProgressScreen(onOpenPersonalBests: () -> Unit, onOpenRun: (runId: String) -> Unit) {
    val vm = appViewModel { c -> ProgressViewModel(c.runRepository, c.core.clock, c.core.dispatchers) }
    val state by vm.state.collectAsState()
    val s = state
    Column(Modifier.fillMaxSize().background(Black)) {
        AppTopBar(title = stringResource(R.string.progress_title))
        if (s == null) return@Column
        if (s.isEmpty) {
            ActivityFilterChips(
                selected = s.filter, onSelect = vm::setFilter,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            EmptyState(
                title = stringResource(R.string.progress_empty_title),
                body = stringResource(R.string.progress_empty_body),
                modifier = Modifier.weight(1f),
            )
            return@Column
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
                    )
                    StatRow(
                        listOf(
                            StatItem(stringResource(R.string.progress_streak_current_days), s.streaks.currentDays.toString(), d),
                            StatItem(stringResource(R.string.progress_streak_longest_days), s.streaks.longestDays.toString(), d),
                        ),
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.progress_totals)) }
            item {
                StatRow(
                    listOf(
                        StatItem(stringResource(R.string.progress_metric_runs), s.allTime.count.toString()),
                        StatItem(stringResource(R.string.progress_metric_distance), formatDistanceKm(s.allTime.distanceM, 0), stringResource(R.string.progress_unit_km)),
                        StatItem(stringResource(R.string.progress_metric_time), formatDurationShort(s.allTime.movingMillis)),
                    ),
                    size = StatSize.M,
                )
            }
            item { PersonalBestsLink(onOpenPersonalBests) }
            item { SectionHeader(stringResource(R.string.progress_recent)) }
            items(s.recent, key = { it.id }) { run -> RecentRunRow(run, onClick = { onOpenRun(run.id) }) }
        }
    }
}

@Composable
private fun metricLabel(m: ProgressMetric): String = when (m) {
    ProgressMetric.DISTANCE -> stringResource(R.string.progress_metric_distance)
    ProgressMetric.TIME -> stringResource(R.string.progress_metric_time)
    ProgressMetric.RUNS -> stringResource(R.string.progress_metric_runs)
    ProgressMetric.ELEVATION -> stringResource(R.string.progress_metric_elevation)
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
        Text(caption, style = AppText.titleM, color = TextPrimary)
        StatRow(
            listOf(
                StatItem(stringResource(R.string.progress_metric_distance), formatDistanceKm(s.selected.distanceM), stringResource(R.string.progress_unit_km)),
                StatItem(stringResource(R.string.progress_metric_time), formatDurationShort(s.selected.movingMillis)),
                StatItem(stringResource(R.string.progress_metric_runs), s.selected.count.toString()),
                StatItem(stringResource(R.string.progress_metric_elevation), formatElevation(s.selected.elevationGainM)),
            ),
            size = StatSize.M,
        )
    }
}

@Composable
private fun PersonalBestsLink(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Raised1)
            .clickable(onClick = onClick)
            .heightIn(min = Sizes.touchTarget)
            .padding(Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.progress_pb_link), style = AppText.titleM, color = TextPrimary)
            Text(stringResource(R.string.progress_pb_link_subtitle), style = AppText.bodyS, color = TextSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextTertiary)
    }
}

@Composable
private fun RecentRunRow(run: Run, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Raised1)
            .clickable(onClick = onClick)
            .heightIn(min = Sizes.touchTarget)
            .padding(Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ActivityTypeIcon(run.type, tint = TextSecondary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(run.name, style = AppText.body, color = TextPrimary)
            Text(formatRunDate(run.startedAtMillis, run.zoneId), style = AppText.bodyS, color = TextSecondary)
        }
        Text(
            stringResource(
                R.string.progress_run_detail,
                formatDistanceKm(run.stats.distanceM) + " " + stringResource(R.string.progress_unit_km),
                formatDurationShort(run.stats.movingMillis),
            ),
            style = AppText.bodyS, color = TextSecondary,
        )
    }
}
