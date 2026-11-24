package com.example.running.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.running.R
import com.example.running.ui.components.ActivityTypeIcon
import com.example.running.ui.components.AppCard
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Spacing

/**
 * Compact one-line version of the Activities card (no map, no stat grid), built from the same
 * [RunCardUi] so a run reads the same everywhere it is listed (Progress "Recent").
 */
@Composable
internal fun RunRow(card: RunCardUi, onClick: () -> Unit, modifier: Modifier = Modifier) = AppCard(modifier, onClick = onClick) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ActivityTypeIcon(card.type, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(card.name, style = AppText.body, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            Text(card.whenText, style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.run_row_summary, card.distanceKm, card.duration),
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
