package com.example.running.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.running.core.model.RecordingState
import com.example.running.di.LocalAppContainer
import com.example.running.ui.activities.ActivitiesScreen
import com.example.running.ui.dev.DevEngineCompareScreen
import com.example.running.ui.onboarding.OnboardingScreen
import com.example.running.ui.progress.PersonalBestsScreen
import com.example.running.ui.progress.ProgressScreen
import com.example.running.ui.record.RecordScreen
import com.example.running.ui.rundetail.RunDetailScreen
import com.example.running.ui.settings.BackupScreen
import com.example.running.ui.settings.ProfileEditScreen
import com.example.running.ui.settings.SettingsScreen
import com.example.running.ui.theme.Black

private fun NavDestination?.tab(): Tab? =
    this?.hierarchy?.firstNotNullOfOrNull { d -> Tab.entries.firstOrNull { d.hasRoute(it.route::class) } }

fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Bottom-tab shell. The Scaffold applies only the bottom-bar padding; screens draw edge-to-edge and
 * handle the status bar themselves.
 *
 * @param openRecordingRequest true when a notification tap asked for the Record tab; [onOpenRecordingHandled]
 *   resets it once navigated.
 */
@Composable
fun AppNavHost(
    onboardingComplete: Boolean,
    openRecordingRequest: Boolean,
    onOpenRecordingHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val container = LocalAppContainer.current
    val recordingState by container.recording.controller.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = backStack?.destination.tab()
    val fullScreenRecording = currentTab == Tab.Record &&
        (recordingState is RecordingState.Recording || recordingState is RecordingState.Saving)
    val recordingActive = recordingState is RecordingState.Recording || recordingState is RecordingState.Saving
    val showBottomBar = currentTab != null && !fullScreenRecording

    LaunchedEffect(openRecordingRequest, onboardingComplete) {
        if (openRecordingRequest && onboardingComplete) {
            navController.navigateToTab(Tab.Record)
            onOpenRecordingHandled()
        }
    }

    Scaffold(
        containerColor = Black,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) BottomBar(currentTab, recordingActive) { navController.navigateToTab(it) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(
                navController = navController,
                startDestination = if (onboardingComplete) RecordRoute else OnboardingRoute,
            ) {
                composable<OnboardingRoute> {
                    OnboardingScreen(onFinished = {
                        navController.navigate(RecordRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                    })
                }
                composable<RecordRoute> {
                    RecordScreen(onRunSaved = { runId -> navController.navigate(RunDetailRoute(runId)) })
                }
                composable<ActivitiesRoute> {
                    ActivitiesScreen(onOpenRun = { navController.navigate(RunDetailRoute(it)) })
                }
                composable<ProgressRoute> {
                    ProgressScreen(
                        onOpenPersonalBests = { navController.navigate(PersonalBestsRoute) },
                        onOpenRun = { navController.navigate(RunDetailRoute(it)) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onOpenProfile = { navController.navigate(ProfileEditRoute) },
                        onOpenBackup = { navController.navigate(BackupRoute) },
                    )
                }
                composable<RunDetailRoute> { entry ->
                    val route = entry.toRoute<RunDetailRoute>()
                    RunDetailScreen(
                        runId = route.runId,
                        onBack = { navController.popBackStack() },
                        onOpenDevCompare = { navController.navigate(DevEngineCompareRoute(it)) },
                    )
                }
                composable<PersonalBestsRoute> {
                    PersonalBestsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenRun = { navController.navigate(RunDetailRoute(it)) },
                    )
                }
                composable<DevEngineCompareRoute> { entry ->
                    DevEngineCompareScreen(runId = entry.toRoute<DevEngineCompareRoute>().runId, onBack = { navController.popBackStack() })
                }
                composable<ProfileEditRoute> { ProfileEditScreen(onBack = { navController.popBackStack() }) }
                composable<BackupRoute> { BackupScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}
