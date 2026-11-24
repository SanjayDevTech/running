package com.example.running.ui.settings

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.running.ui.components.EmptyState

/** CONTRACT(F). Public signature frozen (architecture.md §3.8). FOUNDATION placeholder body. */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    EmptyState(title = "Backup", body = "Not built yet", modifier = Modifier.statusBarsPadding())
}
