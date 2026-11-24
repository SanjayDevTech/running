package com.example.running.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.running.R
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.MapRoute
import com.example.running.di.appViewModel
import com.example.running.map.RouteThumbnail
import com.example.running.ui.components.ActivityFilterChips
import com.example.running.ui.components.ActivityTypeIcon
import com.example.running.ui.components.EmptyState
import com.example.running.ui.components.SectionHeader
import com.example.running.ui.components.StatItem
import com.example.running.ui.components.StatRow
import com.example.running.ui.components.StatSize
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.Radius
import com.example.running.ui.theme.Raised1
import com.example.running.ui.theme.Raised4
import com.example.running.ui.theme.Spacing

/** CONTRACT(D). Public signature frozen (architecture.md §3.8). */
@Composable
fun ActivitiesScreen(onOpenRun: (runId: String) -> Unit) {
    val vm = appViewModel { c -> ActivitiesViewModel(c.runRepository, c.core.clock) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Black).statusBarsPadding()) {
        Text(
            stringResource(R.string.history_title),
            style = AppText.titleL,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        )
        ActivityFilterChips(
            selected = state.filter,
            onSelect = vm::setFilter,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize())
            state.cards.isEmpty() -> {
                if (state.filter == ActivityFilter.ALL) {
                    EmptyState(
                        title = stringResource(R.string.history_empty_title),
                        body = stringResource(R.string.history_empty_body),
                    )
                } else {
                    EmptyState(
                        title = stringResource(R.string.history_empty_filtered_title),
                        body = stringResource(R.string.history_empty_filtered_body),
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.gutter, vertical = Spacing.l,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                state.week?.let { week -> item(key = "week") { WeekStrip(week) } }
                items(state.cards, key = { it.id }) { card ->
                    val route by produceState<MapRoute?>(initialValue = null, card.id) { value = vm.routeFor(card.id) }
                    RunCard(card, route, onClick = { onOpenRun(card.id) })
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(week: WeekSummaryUi) {
    val initials = stringArrayResource(R.array.history_day_initials)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Raised1)
            .padding(Spacing.l),
    ) {
        SectionHeader(stringResource(R.string.history_this_week))
        StatRow(
            stats = listOf(
                StatItem(stringResource(R.string.history_week_distance), week.distanceKm, stringResource(R.string.detail_unit_km)),
                StatItem(stringResource(R.string.history_week_time), week.duration),
                StatItem(stringResource(R.string.history_week_activities), week.count.toString()),
            ),
            size = StatSize.L,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            week.days.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((6 + 42 * day.fraction).dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(
                                    when {
                                        day.isToday && day.hasActivity -> Accent // current-period bar
                                        day.hasActivity -> Raised4
                                        else -> Raised4.copy(alpha = 0.5f)
                                    },
                                ),
                        )
                    }
                    Text(
                        initials.getOrElse(day.dayIndex) { "" },
                        style = AppText.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun RunCard(card: RunCardUi, route: MapRoute?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Raised1)
            .clickable(onClick = onClick)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            ActivityTypeIcon(card.type, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(card.name, style = AppText.titleM, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                Text(card.whenText, style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        StatRow(
            stats = listOf(
                StatItem(stringResource(R.string.history_card_distance), card.distanceKm, stringResource(R.string.detail_unit_km)),
                StatItem(stringResource(R.string.history_card_time), card.duration),
                StatItem(stringResource(R.string.history_card_pace), card.pace, stringResource(R.string.detail_unit_pace)),
            ),
            size = StatSize.M,
        )
        if (route != null) {
            RouteThumbnail(
                route = route,
                cacheKey = card.id,
                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(Radius.sm)),
            )
        }
    }
}
