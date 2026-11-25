package com.example.running.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Danger
import com.example.running.ui.theme.Motion
import com.example.running.ui.theme.Outline
import com.example.running.ui.theme.Raised3
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.TextPrimary

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text, style = AppText.label.copy(fontSize = AppText.body.fontSize))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.button),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.xxl, vertical = Spacing.m),
    ) { ButtonContent(text, icon) }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.button),
        enabled = enabled,
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.xxl, vertical = Spacing.m),
    ) { ButtonContent(text, icon) }
}

@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.button),
        border = androidx.compose.foundation.BorderStroke(1.dp, Danger),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.xxl, vertical = Spacing.m),
    ) { ButtonContent(text, null) }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.controlButton,
    containerColor: Color = Raised3,
    contentColor: Color = TextPrimary,
    enabled: Boolean = true,
) {
    // Enabled/disabled and colour swaps (Start unlocking, Pause -> Resume) fade instead of snapping.
    val defaults = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor, contentColor = contentColor)
    val container by animateColorAsState(
        if (enabled) containerColor else defaults.disabledContainerColor, Motion.state(), label = "circleContainer",
    )
    val content by animateColorAsState(
        if (enabled) contentColor else defaults.disabledContentColor, Motion.state(), label = "circleContent",
    )
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = size, minHeight = size),
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.padding(Spacing.xs).size(size * 0.4f))
    }
}
