package com.example.running.ui.settings

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.running.ui.components.EmptyState

/** CONTRACT(F). Public signature frozen (architecture.md §3.8). FOUNDATION placeholder body. */
@Composable
fun SettingsScreen(onOpenProfile: () -> Unit, onOpenBackup: () -> Unit) {
    EmptyState(title = "Settings", body = "Not built yet", modifier = Modifier.statusBarsPadding())
}
