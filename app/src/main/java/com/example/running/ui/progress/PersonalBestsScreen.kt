package com.example.running.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.running.R
import com.example.running.di.appViewModel
import com.example.running.ui.components.ActivityFilterChips
import com.example.running.ui.components.AppCard
import com.example.running.ui.components.AppTopBar
import com.example.running.ui.components.EmptyState
import com.example.running.ui.components.SectionHeader
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.TextTertiary

/** CONTRACT(E). Public signature frozen (architecture.md §3.8). */
@Composable
fun PersonalBestsScreen(onBack: () -> Unit, onOpenRun: (runId: String) -> Unit) {
    val vm = appViewModel { c -> PersonalBestsViewModel(c.runRepository, c.core.clock, c.core.dispatchers) }
    val state by vm.state.collectAsState()
    val s = state
    Column(Modifier.fillMaxSize().background(Black)) {
        AppTopBar(title = stringResource(R.string.pb_title), onBack = onBack)
        if (s == null) return@Column
        ActivityFilterChips(
            selected = s.filter, onSelect = vm::setFilter,
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        )
        if (s.isEmpty) {
            EmptyState(
                title = stringResource(R.string.pb_empty_title),
                body = stringResource(R.string.pb_empty_body),
                modifier = Modifier.weight(1f),
            )
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item { SectionHeader(stringResource(R.string.pb_section_efforts)) }
            val (efforts, longest) = s.rows.partition { it.kind is PbKind.Effort }
            items(efforts.size) { PbRowItem(efforts[it], onOpenRun) }
            item { SectionHeader(stringResource(R.string.pb_section_longest)) }
            items(longest.size) { PbRowItem(longest[it], onOpenRun) }
        }
    }
}

@Composable
private fun pbTitle(kind: PbKind): String = when (kind) {
    PbKind.LongestDistance -> stringResource(R.string.pb_longest_distance)
    PbKind.LongestDuration -> stringResource(R.string.pb_longest_duration)
    is PbKind.Effort -> kind.distance.label
}

@Composable
private fun PbRowItem(row: PbRow, onOpenRun: (String) -> Unit) {
    val runId = row.runId
    AppCard(onClick = runId?.let { { onOpenRun(it) } }) { Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(pbTitle(row.kind), style = AppText.titleM, color = MaterialTheme.colorScheme.onSurface)
            if (runId != null) {
                Text(
                    stringResource(R.string.pb_detail, row.runName.orEmpty(), row.date.orEmpty()),
                    style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (row.value != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(row.value, style = AppText.numberM, color = Accent)
                if (row.pace != null) Text(row.pace, style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                stringResource(R.string.pb_not_set), style = AppText.bodyS, color = TextTertiary,
                textAlign = TextAlign.End,
            )
        }
    }
}
}
