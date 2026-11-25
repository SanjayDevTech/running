package com.example.running.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Motion
import com.example.running.ui.theme.Spacing

enum class StatSize { Hero, XL, L, M }

data class StatItem(val label: String, val value: String, val unit: String? = null)

/**
 * The value text. With [animate], a new value crossfades in (Motion.crossfade) — for figures that change
 * because the user picked something (a period, a filter), never for live ones that tick every second.
 */
@Composable
private fun StatValue(value: String, style: TextStyle, color: Color, animate: Boolean, modifier: Modifier) {
    if (!animate) {
        Text(text = value, style = style, color = color, maxLines = 1, modifier = modifier)
        return
    }
    AnimatedContent(value, modifier, transitionSpec = { Motion.crossfade() }, label = "statValue") { v ->
        Text(text = v, style = style, color = color, maxLines = 1)
    }
}

private fun StatSize.style(): TextStyle = when (this) {
    StatSize.Hero -> AppText.hero
    StatSize.XL -> AppText.numberXL
    StatSize.L -> AppText.numberL
    StatSize.M -> AppText.numberM
}

/** Label above value (eyebrow), unit trailing the value in bodyS/onSurfaceVariant. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    size: StatSize = StatSize.L,
    valueColor: Color = Color.Unspecified,
    alignment: Alignment.Horizontal = Alignment.Start,
    animateValue: Boolean = false,
) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(
            text = label.uppercase(),
            style = AppText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StatValue(
                value = value,
                style = size.style(),
                color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
                animate = animateValue,
                modifier = Modifier.alignByBaseline(),
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

/** Equal-width columns; wraps to more rows when the font scale makes a column too narrow. */
@Composable
fun StatRow(
    stats: List<StatItem>,
    modifier: Modifier = Modifier,
    size: StatSize = StatSize.L,
    animateValues: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale
    val columns = if (fontScale > 1.5f && stats.size > 2) 2 else stats.size.coerceAtLeast(1)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        maxItemsInEachRow = columns,
    ) {
        stats.forEach { s ->
            StatTile(
                label = s.label, value = s.value, unit = s.unit, size = size, animateValue = animateValues,
                modifier = Modifier.weight(1f).widthIn(min = 0.dp),
            )
        }
    }
}
