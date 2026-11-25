package com.example.running.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.running.R
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.ActivityType
import com.example.running.core.model.GpsQuality
import com.example.running.core.model.LocationEngine
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Motion
import com.example.running.ui.theme.Outline
import com.example.running.ui.theme.Radius
import com.example.running.ui.theme.Raised2
import com.example.running.ui.theme.Raised4
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.TextPrimary
import com.example.running.ui.theme.TextSecondary
import com.example.running.ui.theme.color

/** Single-select pills. Selected = raised fill + primary text, never an accent block. */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val shape = RoundedCornerShape(Radius.pill)
            // Selection is a tint, so it fades like one (Motion.TINT_MS).
            val fill by animateColorAsState(if (isSelected) Raised4 else Raised2, Motion.tint(), label = "chipFill")
            val border by animateColorAsState(if (isSelected) TextSecondary else Outline, Motion.tint(), label = "chipBorder")
            val text by animateColorAsState(if (isSelected) TextPrimary else TextSecondary, Motion.tint(), label = "chipText")
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(shape)
                    .background(fill)
                    .border(1.dp, border, shape)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = {
                            if (!isSelected) haptics.light()
                            onSelect(option)
                        },
                    )
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = AppText.label, color = text)
            }
        }
    }
}

/** "All" "Run" "Walk". */
@Composable
fun ActivityFilterChips(selected: ActivityFilter, onSelect: (ActivityFilter) -> Unit, modifier: Modifier = Modifier) {
    val allLabel = stringResource(R.string.filter_all)
    ChoiceChips(
        options = ActivityFilter.entries,
        selected = selected,
        label = {
            when (it) {
                ActivityFilter.ALL -> allLabel
                ActivityFilter.RUN -> ActivityType.RUN.displayName()
                ActivityFilter.WALK -> ActivityType.WALK.displayName()
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
    val name = engine?.displayName() ?: stringResource(R.string.engine_none)
    val text = if (fallback) stringResource(R.string.engine_with_fallback, name) else name
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(Raised2)
            .heightIn(min = 32.dp)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(quality.color())
        Spacer(Modifier.width(Spacing.s))
        Text(text, style = AppText.label, color = TextPrimary)
    }
}
