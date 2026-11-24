package com.example.running.ui.rundetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.running.R
import com.example.running.core.model.ActivityType
import com.example.running.core.model.RouteMapMode
import com.example.running.core.model.SegmentKind
import com.example.running.core.util.METERS_PER_KM
import com.example.running.core.util.formatDistanceKm
import com.example.running.core.util.formatDuration
import com.example.running.core.util.formatElevation
import com.example.running.core.util.formatPace
import com.example.running.di.appViewModel
import com.example.running.map.RouteMap
import com.example.running.ui.components.ActivityTypeIcon
import com.example.running.ui.components.AppCard
import com.example.running.ui.components.AppDivider
import com.example.running.ui.components.AppTopBar
import com.example.running.ui.components.CircleIconButton
import com.example.running.ui.components.ConfirmDialog
import com.example.running.ui.components.EmptyState
import com.example.running.ui.components.PbBadge
import com.example.running.ui.components.SectionHeader
import com.example.running.ui.components.StatItem
import com.example.running.ui.components.StatRow
import com.example.running.ui.components.StatSize
import com.example.running.ui.components.TextInputDialog
import com.example.running.ui.components.charts.AppLineChart
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.Danger
import com.example.running.ui.theme.Radius
import com.example.running.ui.theme.Raised3
import com.example.running.ui.theme.Raised4
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.color

/** CONTRACT(D). Public signature frozen (architecture.md §3.8). */
@Composable
fun RunDetailScreen(runId: String, onBack: () -> Unit, onOpenDevCompare: (runId: String) -> Unit) {
    val vm = appViewModel(key = "run-detail-$runId") { c ->
        RunDetailViewModel(
            runId = runId,
            repository = c.runRepository,
            settings = c.settings,
            seriesBuilder = c.analysis.runSeriesBuilder,
            offline = c.map.offline,
            dispatchers = c.core.dispatchers,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        RunDetailUiState.Loading -> Box(Modifier.fillMaxSize().background(Black))
        RunDetailUiState.NotFound -> Column(Modifier.fillMaxSize().background(Black)) {
            AppTopBar(title = stringResource(R.string.detail_title), onBack = onBack)
            EmptyState(
                title = stringResource(R.string.detail_not_found_title),
                body = stringResource(R.string.detail_not_found_body),
            )
        }
        is RunDetailUiState.Content -> DetailContent(
            content = s.content,
            onBack = onBack,
            onRename = vm::rename,
            onSetType = vm::setType,
            onDelete = { vm.delete(onBack) },
            onOpenDevCompare = { onOpenDevCompare(runId) },
        )
    }
}

@Composable
private fun DetailContent(
    content: RunDetailContent,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onSetType: (ActivityType) -> Unit,
    onDelete: () -> Unit,
    onOpenDevCompare: () -> Unit,
) {
    val run = content.run
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var mapExpanded by rememberSaveable { mutableStateOf(false) }
    var highlightIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val highlight = highlightIndex?.let { i -> content.splits.firstOrNull { it.index == i } }

    BackHandler(enabled = mapExpanded) { mapExpanded = false }

    Box(Modifier.fillMaxSize().background(Black)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = run.name,
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.detail_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Raised3) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_menu_rename)) },
                                onClick = { menuOpen = false; renaming = true },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (content.otherType == ActivityType.WALK) R.string.detail_menu_change_to_walk
                                            else R.string.detail_menu_change_to_run,
                                        ),
                                    )
                                },
                                onClick = { menuOpen = false; onSetType(content.otherType) },
                            )
                            if (content.showDevCompare) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.detail_menu_compare_engines)) },
                                    onClick = { menuOpen = false; onOpenDevCompare() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_delete), color = Danger) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    ActivityTypeIcon(run.type, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(content.subtitle, style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                content.geometry.route?.let { route ->
                    RouteMap(
                        route = route,
                        mode = RouteMapMode.STATIC,
                        animateDrawIn = true,
                        highlightDistanceM = highlight?.let { it.startDistanceM..it.endDistanceM },
                        onClick = { mapExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(Sizes.mapHeight),
                    )
                }

                Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    val h = content.headline
                    StatRow(
                        listOf(
                            StatItem(stringResource(R.string.stat_distance), h.distanceKm, stringResource(R.string.unit_km)),
                            StatItem(stringResource(R.string.detail_stat_moving_time), h.movingTime),
                        ),
                        size = StatSize.XL,
                    )
                    StatRow(
                        listOf(
                            StatItem(stringResource(R.string.detail_stat_pace), h.pace, stringResource(R.string.unit_pace)),
                            StatItem(stringResource(R.string.detail_stat_elapsed), h.elapsed),
                            StatItem(stringResource(R.string.stat_elevation), h.elevation),
                        ),
                        size = StatSize.L,
                    )
                    StatRow(
                        listOf(
                            StatItem(stringResource(R.string.stat_calories), h.calories, stringResource(R.string.unit_kcal)),
                            StatItem(stringResource(R.string.stat_cadence), h.cadence, stringResource(R.string.unit_spm)),
                        ),
                        size = StatSize.L,
                    )

                    val pbs = content.efforts.filter { it.isPersonalBest }
                    if (pbs.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) { pbs.forEach { PbBadge(stringResource(R.string.detail_pb_badge, it.label)) } }
                    }
                }

                if (content.geometry.pace.size >= 2) {
                    Column(Modifier.padding(horizontal = Spacing.gutter)) {
                        SectionHeader(stringResource(R.string.detail_section_pace))
                        AppLineChart(
                            points = content.geometry.pace,
                            breaks = content.geometry.paceBreaksKm,
                            yFormatter = { formatPace(it) },
                            xFormatter = { formatDistanceKm(it * METERS_PER_KM, 1) },
                            minXLabelStep = 0.1, // one decimal km
                            invertY = true,
                            filled = false,
                        )
                    }
                }
                if (content.geometry.elevation.size >= 2) {
                    Column(Modifier.padding(horizontal = Spacing.gutter)) {
                        SectionHeader(stringResource(R.string.detail_section_elevation))
                        AppLineChart(
                            points = content.geometry.elevation,
                            yFormatter = { formatElevation(it) },
                            xFormatter = { formatDistanceKm(it * METERS_PER_KM, 1) },
                            minXLabelStep = 0.1, // one decimal km
                            minYStep = 1.0, // formatElevation shows whole metres
                        )
                    }
                }

                if (content.splits.isNotEmpty()) {
                    SplitsTable(
                        rows = content.splits,
                        selectedIndex = highlightIndex,
                        onSelect = { highlightIndex = if (highlightIndex == it) null else it },
                    )
                }
                content.segments?.let { SegmentsSection(it) }
                if (content.efforts.isNotEmpty()) EffortsSection(content.efforts)
            }
        }

        if (mapExpanded) {
            Box(Modifier.fillMaxSize().background(Black)) {
                RouteMap(
                    route = content.geometry.route,
                    mode = RouteMapMode.INTERACTIVE,
                    highlightDistanceM = highlight?.let { it.startDistanceM..it.endDistanceM },
                    modifier = Modifier.fillMaxSize(),
                )
                CircleIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.detail_map_close),
                    onClick = { mapExpanded = false },
                    modifier = Modifier.statusBarsPadding().padding(Spacing.l).align(Alignment.TopStart),
                )
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.detail_rename_title),
            initial = run.name,
            confirmLabel = stringResource(R.string.detail_rename_confirm),
            onConfirm = { renaming = false; onRename(it) },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.detail_delete_title),
            text = stringResource(R.string.detail_delete_body),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun SplitsTable(rows: List<SplitRowUi>, selectedIndex: Int?, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.gutter)) {
        SectionHeader(stringResource(R.string.detail_section_splits))
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
            Text(stringResource(R.string.detail_splits_km).uppercase(), style = AppText.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.18f))
            Text(stringResource(R.string.detail_splits_pace).uppercase(), style = AppText.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.22f))
            Spacer(Modifier.weight(0.42f))
            Text(stringResource(R.string.detail_splits_elev).uppercase(), style = AppText.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.18f))
        }
        rows.forEach { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(if (selectedIndex == r.index) Raised4 else Black)
                    .clickable { onSelect(r.index) }
                    .heightIn(min = Sizes.touchTarget)
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.label, style = AppText.numberM, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.18f))
                Text(r.pace, style = AppText.numberM, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.22f))
                Box(Modifier.weight(0.42f).padding(end = Spacing.m)) {
                    Box(
                        Modifier
                            .fillMaxWidth(r.barFraction)
                            .height(10.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(if (r.isFastest) Accent else Raised4),
                    )
                }
                Text(r.elevation, style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.18f))
            }
            AppDivider()
        }
    }
}

@Composable
private fun SegmentsSection(segments: SegmentsUi) {
    Column(Modifier.padding(horizontal = Spacing.gutter)) {
        SectionHeader(stringResource(R.string.detail_section_segments))
        Text(
            stringResource(R.string.detail_segments_summary, formatDuration(segments.runMillis), formatDuration(segments.walkMillis)),
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
        AppCard(contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            segments.rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 20.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(r.kind.color()),
                    )
                    Text(
                        stringResource(
                            when (r.kind) {
                                SegmentKind.RUN -> R.string.detail_segment_run
                                SegmentKind.WALK -> R.string.detail_segment_walk
                                SegmentKind.STOP -> R.string.detail_segment_stop
                            },
                        ),
                        style = AppText.body,
                        modifier = Modifier.weight(1f),
                    )
                    Text(r.duration, style = AppText.numberM)
                    Text(r.distanceKm, style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(r.pace, style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EffortsSection(efforts: List<EffortUi>) {
    Column(Modifier.padding(horizontal = Spacing.gutter)) {
        SectionHeader(stringResource(R.string.detail_section_best_efforts))
        efforts.forEach { e ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(e.label, style = AppText.body, modifier = Modifier.weight(1f))
                Text(e.time, style = AppText.numberM)
                if (e.isPersonalBest) {
                    PbBadge(stringResource(R.string.history_pb_badge))
                } else {
                    Text(
                        stringResource(R.string.detail_effort_rank, e.rank),
                        style = AppText.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppDivider()
        }
    }
}
