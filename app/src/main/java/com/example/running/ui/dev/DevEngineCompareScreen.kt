package com.example.running.ui.dev

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.running.ui.components.EmptyState

/** CONTRACT(A). Public signature frozen (architecture.md §3.8). FOUNDATION placeholder body. */
@Composable
fun DevEngineCompareScreen(runId: String, onBack: () -> Unit) {
    EmptyState(title = "Compare engines", body = "Not built yet", modifier = Modifier.statusBarsPadding())
}
