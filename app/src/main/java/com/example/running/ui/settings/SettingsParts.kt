package com.example.running.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Hairline
import com.example.running.ui.theme.Radius
import com.example.running.ui.theme.Raised1
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Spacing

/** Raised card that groups rows; rows are separated by hairlines. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(Raised1),
    ) { content() }
}

@Composable
internal fun RowDivider() = HorizontalDivider(color = Hairline, modifier = Modifier.padding(horizontal = Spacing.l))

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
        if (statusColor != null) Row(Modifier.size(10.dp).clip(CircleShape).background(statusColor)) {}
        Column(Modifier.weight(1f)) {
            Text(title, style = AppText.body, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) Text(summary, style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onAction) { Text(actionLabel, style = AppText.label) }
        }
    }
}
