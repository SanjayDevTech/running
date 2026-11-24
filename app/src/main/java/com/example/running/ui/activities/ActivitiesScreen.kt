package com.example.running.ui.activities

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.running.ui.components.EmptyState

/** CONTRACT(D). Public signature frozen (architecture.md §3.8). FOUNDATION placeholder body. */
@Composable
fun ActivitiesScreen(onOpenRun: (runId: String) -> Unit) {
    EmptyState(title = "Activities", body = "Not built yet", modifier = Modifier.statusBarsPadding())
}
