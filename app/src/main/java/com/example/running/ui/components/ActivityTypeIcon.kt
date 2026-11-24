package com.example.running.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.running.core.model.ActivityType
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode

@Composable
fun ActivityTypeIcon(type: ActivityType, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(
        imageVector = when (type) {
            ActivityType.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
            ActivityType.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
        },
        contentDescription = type.displayName(),
        modifier = modifier,
        tint = tint,
    )
}

fun ActivityType.displayName(): String = when (this) {
    ActivityType.RUN -> "Run"
    ActivityType.WALK -> "Walk"
}

fun LocationEngineMode.displayName(): String = when (this) {
    LocationEngineMode.AUTO -> "Auto"
    LocationEngineMode.GOOGLE -> "Google"
    LocationEngineMode.BUILT_IN -> "Built-in"
}

fun LocationEngine.displayName(): String = when (this) {
    LocationEngine.PLAY -> "Google"
    LocationEngine.PLATFORM -> "Built-in"
}
