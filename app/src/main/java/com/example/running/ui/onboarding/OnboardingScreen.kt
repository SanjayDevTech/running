package com.example.running.ui.onboarding

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.example.running.di.LocalAppContainer
import com.example.running.ui.components.EmptyState
import kotlinx.coroutines.launch

/**
 * CONTRACT(F). Public signature frozen (architecture.md §3.8). FOUNDATION placeholder body:
 * "Skip" marks onboarding complete so the other tracks can reach the tabs. F owns setting
 * onboarding_complete once the real flow finishes.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    EmptyState(
        title = "Welcome",
        body = "Not built yet",
        modifier = Modifier.statusBarsPadding(),
        actionLabel = "Skip",
        onAction = {
            scope.launch {
                container.settings.setOnboardingComplete(true)
                onFinished()
            }
        },
    )
}
