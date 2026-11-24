package com.example.running.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.GpsQuality
import com.example.running.core.model.LocationEngine
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Danger
import com.example.running.ui.theme.Outline
import com.example.running.ui.theme.Radius
import com.example.running.ui.theme.Raised2
import com.example.running.ui.theme.Raised4
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.Success
import com.example.running.ui.theme.TextPrimary
import com.example.running.ui.theme.TextSecondary
import com.example.running.ui.theme.TextTertiary
import com.example.running.ui.theme.Warning

/** Single-select pills. Selected = raised fill + primary text, never an accent block. */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val shape = RoundedCornerShape(Radius.pill)
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(shape)
                    .background(if (isSelected) Raised4 else Raised2)
                    .border(1.dp, if (isSelected) TextSecondary else Outline, shape)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = AppText.label, color = if (isSelected) TextPrimary else TextSecondary)
            }
        }
    }
}

/** "All" "Run" "Walk". */
@Composable
fun ActivityFilterChips(selected: ActivityFilter, onSelect: (ActivityFilter) -> Unit, modifier: Modifier = Modifier) {
    ChoiceChips(
        options = ActivityFilter.entries,
        selected = selected,
        label = {
            when (it) {
                ActivityFilter.ALL -> "All"
                ActivityFilter.RUN -> "Run"
                ActivityFilter.WALK -> "Walk"
            }
        },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/** "Google" / "Built-in" (+ "fallback"), dot coloured by GPS quality. */
@Composable
fun EngineChip(
    engine: LocationEngine?,
    fallback: Boolean,
    modifier: Modifier = Modifier,
    quality: GpsQuality? = null,
) {
    val dot = when (quality) {
        GpsQuality.GOOD -> Success
        GpsQuality.FAIR -> Warning
        GpsQuality.POOR -> Danger
        GpsQuality.NONE, null -> TextTertiary
    }
    val text = buildString {
        append(engine?.displayName() ?: "No GPS")
        if (fallback) append(" · fallback")
    }
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(Raised2)
            .heightIn(min = 32.dp)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(Spacing.s))
        Text(text, style = AppText.label, color = TextPrimary)
    }
}
