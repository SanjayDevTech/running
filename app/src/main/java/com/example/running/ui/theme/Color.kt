package com.example.running.ui.theme

import androidx.compose.ui.graphics.Color

// Always-dark OLED palette. Pure black where content sits on the canvas; raised near-blacks where
// a pure-black surface would make a shape vanish (cards, sheets, dialogs, bottom bar, chips).

val Black = Color(0xFF000000)          // background, surface, surfaceContainerLowest
val Raised1 = Color(0xFF0F0F11)        // surfaceContainerLow     — list cards
val Raised2 = Color(0xFF16161A)        // surfaceContainer        — bottom bar, chips
val Raised3 = Color(0xFF1E1E23)        // surfaceContainerHigh    — sheets, dialogs, menus
val Raised4 = Color(0xFF27272D)        // surfaceContainerHighest — pressed/selected fills
val Hairline = Color(0xFF26262B)       // outlineVariant (dividers)
val Outline = Color(0xFF3C3C44)        // outline (chip borders)
val TextPrimary = Color(0xFFF3F3F5)    // onSurface / onBackground
val TextSecondary = Color(0xFFA3A3AD)  // onSurfaceVariant
val TextTertiary = Color(0xFF6E6E78)
val Accent = Color(0xFFFF6A2B)         // primary — record button, selected tab, PB, links
val OnAccent = Color(0xFF1A0800)       // onPrimary (dark text on orange)
val AccentMuted = Color(0xFF3A1A0C)    // primaryContainer (filled area under charts)
val Walk = Color(0xFF4CB8FF)           // walk segments / walk filter
val Stop = Color(0xFF6E6E78)           // stop segments
val Success = Color(0xFF3DDC97)
val Warning = Color(0xFFFFC24B)        // weak GPS
val Danger = Color(0xFFFF5A5F)         // error, stop/finish, delete
val StepFilled = Color(0xFF9A9AA6)     // dashed step-filled route parts

/** Fast → slow. */
val PaceScale = listOf(
    Color(0xFF3DDC97), Color(0xFFB8E04A), Color(0xFFFFC24B), Color(0xFFFF6A2B), Color(0xFFFF3B5C),
)
