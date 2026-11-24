package com.example.running.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** No light scheme, no dynamic colour. */
internal val RunningColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentMuted,
    onPrimaryContainer = Accent,
    inversePrimary = Accent,
    secondary = TextSecondary,
    onSecondary = Black,
    secondaryContainer = Raised4,
    onSecondaryContainer = TextPrimary,
    tertiary = Walk,
    onTertiary = Black,
    tertiaryContainer = Raised3,
    onTertiaryContainer = Walk,
    background = Black,
    onBackground = TextPrimary,
    surface = Black,
    onSurface = TextPrimary,
    surfaceVariant = Raised2,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Black,
    inverseSurface = TextPrimary,
    inverseOnSurface = Black,
    error = Danger,
    onError = Black,
    errorContainer = Raised3,
    onErrorContainer = Danger,
    outline = Outline,
    outlineVariant = Hairline,
    scrim = Black,
    surfaceBright = Raised3,
    surfaceDim = Black,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Raised1,
    surfaceContainer = Raised2,
    surfaceContainerHigh = Raised3,
    surfaceContainerHighest = Raised4,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RunningTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = RunningColorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        content = content,
    )
}
