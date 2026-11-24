package com.example.running.ui.progress

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.running.ui.components.EmptyState

/** CONTRACT(E). Public signature frozen (architecture.md §3.8). FOUNDATION placeholder body. */
@Composable
fun ProgressScreen(onOpenPersonalBests: () -> Unit, onOpenRun: (runId: String) -> Unit) {
    EmptyState(title = "Progress", body = "Not built yet", modifier = Modifier.statusBarsPadding())
}
