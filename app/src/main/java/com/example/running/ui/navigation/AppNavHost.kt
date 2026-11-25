package com.example.running.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.navigation.toRoute
import com.example.running.core.model.RecordingEvent
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
import com.example.running.ui.theme.Motion

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
    // Only whether a run is active: the full state changes every second while recording.
    val controllerState = container.recording.controller.state
    val recordingActive by remember(controllerState) {
        controllerState.map { it.isActiveRun() }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = controllerState.value.isActiveRun())
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = backStack?.destination.tab()
    val fullScreenRecording = currentTab == Tab.Record && recordingActive
    val showBottomBar = currentTab != null && !fullScreenRecording

    // RecordScreen opens the saved run itself; a run finished from the notification while another
    // destination is showing would otherwise only appear in Activities.
    val tabNow by rememberUpdatedState(currentTab)
    LaunchedEffect(container) {
        container.recording.controller.events.collect { event ->
            if (event is RecordingEvent.RunSaved && tabNow != Tab.Record) navController.navigate(RunDetailRoute(event.runId))
        }
    }

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
            // Slides away with the Record screen going full-screen (and with pushed screens).
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn(Motion.state()) + expandVertically(Motion.state()),
                exit = fadeOut(Motion.state()) + shrinkVertically(Motion.state()),
            ) {
                BottomBar(currentTab, recordingActive) { navController.navigateToTab(it) }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(
                navController = navController,
                startDestination = if (onboardingComplete) RecordRoute else OnboardingRoute,
                enterTransition = navEnter,
                exitTransition = navExit,
                popEnterTransition = navPopEnter,
                popExitTransition = navPopExit,
                predictivePopEnterTransition = navPredictivePopEnter,
                predictivePopExitTransition = navPredictivePopExit,
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

/*
 * One set of transitions, chosen per pair of entries (gym-tracker's AppNav). Tab <-> tab is a
 * fade-through: 90 ms out, then 210 ms in, so the outgoing tab is gone before the incoming one starts;
 * peers do not slide, a slide says "deeper" or "back". Onboarding -> Record replaces the root, so it
 * fades through too. Everything else is a push or pop: a 300 ms slide with the screen underneath
 * moving a third of the way (parallax).
 *
 * The predictivePop* pair matters as much as pop*: a pop driven by the predictive-back gesture uses its
 * own transitions, which default to a fade + scale-out. Pointing them at the same motion keeps the
 * gesture and the back button identical. (The Int is the swipe edge; unused.)
 */
private fun NavDestination.isPeerRoot(): Boolean = tab() != null || hasRoute(OnboardingRoute::class)

private fun RecordingState.isActiveRun(): Boolean = this is RecordingState.Recording || this is RecordingState.Saving

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPeerSwitch(): Boolean =
    initialState.destination.isPeerRoot() && targetState.destination.isPeerRoot()

private val navEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isPeerSwitch()) Motion.fadeThroughIn else slideIntoContainer(SlideDirection.Start, Motion.nav())
}

private val navExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isPeerSwitch()) Motion.fadeThroughOut else slideOutOfContainer(SlideDirection.Start, Motion.nav()) { it / 3 }
}

private val navPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isPeerSwitch()) Motion.fadeThroughIn else slideIntoContainer(SlideDirection.End, Motion.nav()) { it / 3 }
}

private val navPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isPeerSwitch()) Motion.fadeThroughOut else slideOutOfContainer(SlideDirection.End, Motion.nav())
}

private val navPredictivePopEnter:
    AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { navPopEnter() }

private val navPredictivePopExit:
    AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { navPopExit() }
