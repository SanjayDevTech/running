package com.example.running.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Spacing

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AppText.titleM, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (trailing != null) {
            val clickable = onTrailingClick != null
            Text(
                text = trailing,
                style = AppText.label,
                color = if (clickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .then(if (clickable) Modifier.clickable(onClick = onTrailingClick) else Modifier)
                    .padding(vertical = Spacing.s, horizontal = Spacing.xs),
            )
        }
    }
}
