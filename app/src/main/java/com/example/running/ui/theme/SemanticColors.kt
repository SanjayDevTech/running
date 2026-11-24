package com.example.running.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.running.core.model.GpsQuality
import com.example.running.core.model.SegmentKind

// The one mapping from domain states to semantic colours (docs/ui-house-rules.md §3).

/** Good fix → Success; any weak fix → Warning (Danger is for stop/finish, delete and errors). */
fun GpsQuality?.color(): Color = when (this) {
    GpsQuality.GOOD -> Success
    GpsQuality.FAIR, GpsQuality.POOR -> Warning
    GpsQuality.NONE, null -> TextTertiary
}

/** Route lines and segment swatches. Unclassified (null) parts draw as running. */
fun SegmentKind?.color(): Color = when (this) {
    SegmentKind.WALK -> Walk
    SegmentKind.STOP -> Stop
    SegmentKind.RUN, null -> Accent
}
