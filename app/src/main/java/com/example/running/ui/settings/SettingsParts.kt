package com.example.running.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.running.ui.components.AppCard
import com.example.running.ui.components.AppDivider
import com.example.running.ui.components.StatusDot
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Motion
import com.example.running.ui.theme.Spacing

/** Raised card that groups rows (rows pad themselves); rows are separated by [RowDivider]. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    // Rows coming and going (an action, a reset row, a deleted backup) resize the card smoothly.
    AppCard(modifier.animateContentSize(Motion.state()), contentPadding = PaddingValues(0.dp)) { content() }

@Composable
internal fun RowDivider() = AppDivider(Modifier.padding(horizontal = Spacing.l))

/** Title + one-line supporting text on the left, optional status dot and action on the right. */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    statusColor: Color? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Sizes.touchTarget + Spacing.s)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (statusColor != null) {
            val dot by animateColorAsState(statusColor, Motion.state(), label = "settingsStatus")
            StatusDot(dot)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AppText.body, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) Text(summary, style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onAction) { Text(actionLabel, style = AppText.label) }
        }
    }
}
