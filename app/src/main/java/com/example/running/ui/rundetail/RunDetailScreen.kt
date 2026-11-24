package com.example.running.ui.rundetail

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.running.ui.components.EmptyState

/** CONTRACT(D). Public signature frozen (architecture.md §3.8). FOUNDATION placeholder body. */
@Composable
fun RunDetailScreen(runId: String, onBack: () -> Unit, onOpenDevCompare: (runId: String) -> Unit) {
    EmptyState(title = "Run", body = "Not built yet", modifier = Modifier.statusBarsPadding())
}
