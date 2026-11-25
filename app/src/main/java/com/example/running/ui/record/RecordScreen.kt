package com.example.running.ui.record

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.running.R
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.GpsStatus
import com.example.running.core.model.MapRoute
import com.example.running.core.model.RecordingEvent
import com.example.running.core.model.RecordingState
import com.example.running.core.model.RouteMapMode
import com.example.running.core.system.Permissions
import com.example.running.core.util.formatAccuracy
import com.example.running.core.util.formatCadence
import com.example.running.core.util.formatCalories
import com.example.running.core.util.formatDistanceKm
import com.example.running.core.util.formatDuration
import com.example.running.core.util.formatElevation
import com.example.running.core.util.formatPace
import com.example.running.core.util.formatRunDateTime
import com.example.running.di.LocalAppContainer
import com.example.running.di.appViewModel
import com.example.running.map.RouteMap
import com.example.running.ui.components.CircleIconButton
import com.example.running.ui.components.EngineChip
import com.example.running.ui.components.PrimaryButton
import com.example.running.ui.components.ScreenTitle
import com.example.running.ui.components.SecondaryButton
import com.example.running.ui.components.StatItem
import com.example.running.ui.components.StatRow
import com.example.running.ui.components.StatSize
import com.example.running.ui.components.StatTile
import com.example.running.ui.components.StatusDot
import com.example.running.ui.components.WarningBanner
import com.example.running.ui.components.displayName
import com.example.running.ui.components.rememberAppHaptics
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.Danger
import com.example.running.ui.theme.Motion
import com.example.running.ui.theme.OnAccent
import com.example.running.ui.theme.Radius
import com.example.running.ui.theme.Raised2
import com.example.running.ui.theme.Raised3
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.TextTertiary
import com.example.running.ui.theme.Warning
import com.example.running.ui.theme.color
import kotlinx.coroutines.launch

/** CONTRACT(A). Public signature frozen (architecture.md §3.8). */
@Composable
fun RecordScreen(onRunSaved: (runId: String) -> Unit) {
    val container = LocalAppContainer.current
    val vm = appViewModel { c ->
        RecordViewModel(c.recording.controller, c.recording.controllerImpl.userLocation, c.settings, c.setup.accuracyChecker)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val route by vm.liveRoute.collectAsStateWithLifecycle()
    val userLocation by vm.userLocation.collectAsStateWithLifecycle()
    val devDual by vm.devDualEngine.collectAsStateWithLifecycle()
    val accuracyOff by vm.accuracyOff.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val currentOnRunSaved by rememberUpdatedState(onRunSaved)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is RecordingEvent.RunSaved -> currentOnRunSaved(event.runId)
                is RecordingEvent.Failure -> snackbar.showSnackbar(event.message)
                is RecordingEvent.EngineSwitched ->
                    snackbar.showSnackbar(resources.getString(R.string.record_engine_switched, event.to.displayName()))
            }
        }
    }

    var hasLocation by remember { mutableStateOf(Permissions.hasFineLocation(context)) }
    // Live: the quick-settings toggle does not pause the activity, so a resume check would miss it.
    val locationOnFlow = remember(context) { Permissions.locationEnabledFlow(context) }
    val locationOn by locationOnFlow.collectAsStateWithLifecycle(initialValue = Permissions.isLocationEnabled(context))
    var locationAsked by rememberSaveable { mutableStateOf(false) }
    var startPermsAsked by rememberSaveable { mutableStateOf(false) }
    // Start was tapped while location was blocked: carry on to the GPS-gated start once it is not.
    var startPending by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        hasLocation = Permissions.hasFineLocation(context)
        vm.refreshAccuracy()
        onPauseOrDispose { }
    }
    LaunchedEffect(locationOn) { vm.refreshAccuracy() }
    LifecycleStartEffect(Unit) {
        visible = true
        onStopOrDispose {
            visible = false
            startPending = false
            vm.endWarmUp()
        }
    }
    val block = locationBlock(hasPermission = hasLocation, locationOn = locationOn)
    val canWarmUp = block == null
    val isIdle = state is RecordingState.Idle
    LaunchedEffect(isIdle, canWarmUp, visible) {
        if (isIdle && canWarmUp && visible) vm.beginWarmUp()
    }
    LaunchedEffect(canWarmUp, state is RecordingState.WarmingUp) {
        if (!canWarmUp && state is RecordingState.WarmingUp) vm.endWarmUp()
    }

    // Notifications (live stats) and activity recognition (steps) are asked once, right before the first start.
    val startPermsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.start()
    }
    val accuracyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.refreshAccuracy()
    }
    val turnOnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // OK: the location broadcast flips the banner and warm-up starts; a pending Start goes on from there.
        if (result.resultCode != Activity.RESULT_OK) startPending = false
    }

    val launchRun: () -> Unit = {
        val missing = buildList {
            if (!Permissions.hasNotifications(context)) add(Permissions.NOTIFICATIONS)
            if (!Permissions.hasActivityRecognition(context)) add(Permissions.ACTIVITY_RECOGNITION)
        }
        if (missing.isNotEmpty() && !startPermsAsked) {
            startPermsAsked = true
            startPermsLauncher.launch(missing.toTypedArray())
        } else {
            vm.start()
        }
    }
    // Google Maps style: Play's one-tap dialog in place; Settings only without Play services.
    val turnOnLocation: () -> Unit = {
        scope.launch {
            val resolution = vm.turnOnLocationResolution()
            val launched = resolution != null && runCatching {
                turnOnLauncher.launch(IntentSenderRequest.Builder(resolution).build())
            }.isSuccess
            if (!launched && !Permissions.isLocationEnabled(context)) {
                startPending = false
                context.startActivity(Permissions.locationSettingsIntent())
            }
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = Permissions.hasFineLocation(context)
        locationAsked = true
        when {
            !hasLocation -> startPending = false
            startPending && !Permissions.isLocationEnabled(context) -> turnOnLocation()
        }
    }
    val requestLocationPermission: () -> Unit = {
        if (locationAsked) {
            startPending = false
            context.startActivity(Permissions.appSettingsIntent(context))
        } else {
            locationLauncher.launch(Permissions.LOCATION)
        }
    }
    val fixBlock: () -> Unit = {
        when (block) {
            LocationBlock.PERMISSION -> requestLocationPermission()
            LocationBlock.LOCATION_OFF -> turnOnLocation()
            null -> Unit
        }
    }
    val onStart: () -> Unit = {
        if (block == null) {
            launchRun()
        } else {
            startPending = true
            fixBlock()
        }
    }
    val warmGps = (state as? RecordingState.WarmingUp)?.gps
    LaunchedEffect(startPending, block, warmGps?.startAllowed) {
        if (shouldAutoStart(startPending, block, warmGps)) {
            startPending = false
            launchRun()
        }
    }
    val onFixAccuracy: () -> Unit = {
        scope.launch {
            vm.accuracyResolution()?.let { accuracyLauncher.launch(IntentSenderRequest.Builder(it).build()) }
        }
    }

    Box(Modifier.fillMaxSize().background(Black)) {
        // Phases fade through (ready -> recording -> saving); updates inside a phase do not re-animate.
        AnimatedContent(
            targetState = state,
            contentKey = { it.phase() },
            transitionSpec = { Motion.fadeThrough() },
            label = "recordPhase",
        ) { s ->
            when (s) {
                is RecordingState.Recording -> RecordingContent(
                    state = s,
                    route = route,
                    userLocation = userLocation,
                    devDual = devDual,
                    locationOff = !locationOn,
                    onTurnOnLocation = turnOnLocation,
                    onPause = vm::pause,
                    onResume = vm::resume,
                    onFinish = vm::stop,
                )
                is RecordingState.Saving -> SavingContent()
                is RecordingState.NeedsRecoveryDecision -> RecoveryContent(
                    state = s,
                    zoneId = container.core.clock.zoneId().id,
                    onResume = { vm.resumeInterrupted(s.runId) },
                    onFinish = { vm.finishInterrupted(s.runId) },
                )
                is RecordingState.Idle, is RecordingState.WarmingUp -> {
                    val gps = (s as? RecordingState.WarmingUp)?.gps ?: GpsStatus.NONE
                    ReadyContent(
                        gps = gps,
                        userLocation = if (block == null) userLocation else null,
                        block = block,
                        permissionAsked = locationAsked,
                        accuracyOff = accuracyOff && block == null,
                        devDual = devDual,
                        startEnabled = startEnabled(block, gps, startPending),
                        waitingToStart = startPending && block == null,
                        onStart = onStart,
                        onCancelStart = { startPending = false },
                        onFixBlock = fixBlock,
                        onFixAccuracy = onFixAccuracy,
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.gutter))
    }
}

/** The location banner over the Record map; opens down from its top edge and folds back. */
@Composable
private fun LocationBanner(block: LocationBlock?, permissionAsked: Boolean, onFix: () -> Unit, modifier: Modifier = Modifier) {
    // Keep the last block while the banner animates out, so its text does not blank mid-exit.
    var shown by remember { mutableStateOf(block ?: LocationBlock.LOCATION_OFF) }
    if (block != null) shown = block
    AnimatedVisibility(visible = block != null, modifier = modifier, enter = Motion.appear, exit = Motion.disappear) {
        when (shown) {
            LocationBlock.PERMISSION -> WarningBanner(
                text = stringResource(R.string.record_banner_permission),
                actionLabel = stringResource(
                    if (permissionAsked) R.string.record_banner_permission_settings else R.string.record_banner_permission_allow,
                ),
                onAction = onFix,
            )
            LocationBlock.LOCATION_OFF -> WarningBanner(
                text = stringResource(R.string.record_banner_location_off),
                actionLabel = stringResource(R.string.record_banner_location_off_action),
                onAction = onFix,
            )
        }
    }
}

/** Record phases that replace the whole screen (fade-through between them). */
private enum class RecordPhase { READY, RECORDING, SAVING, RECOVERY }

private fun RecordingState.phase(): RecordPhase = when (this) {
    is RecordingState.Idle, is RecordingState.WarmingUp -> RecordPhase.READY
    is RecordingState.Recording -> RecordPhase.RECORDING
    is RecordingState.Saving -> RecordPhase.SAVING
    is RecordingState.NeedsRecoveryDecision -> RecordPhase.RECOVERY
}

// ------------------------------------------------------------------------------------------ before start

@Composable
private fun ReadyContent(
    gps: GpsStatus,
    userLocation: GeoPoint?,
    block: LocationBlock?,
    permissionAsked: Boolean,
    accuracyOff: Boolean,
    devDual: Boolean,
    startEnabled: Boolean,
    waitingToStart: Boolean,
    onStart: () -> Unit,
    onCancelStart: () -> Unit,
    onFixBlock: () -> Unit,
    onFixAccuracy: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.record_title)) {
            EngineChip(engine = gps.engine, fallback = gps.fallbackActive, quality = gps.quality)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            RouteMap(
                route = null,
                mode = RouteMapMode.LIVE,
                userLocation = userLocation,
                modifier = Modifier.fillMaxSize(),
            )
            LocationBanner(
                block = block,
                permissionAsked = permissionAsked,
                onFix = onFixBlock,
                modifier = Modifier.align(Alignment.TopCenter).padding(Spacing.gutter),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            AnimatedVisibility(block == null, enter = Motion.appear, exit = Motion.disappear) { GpsLine(gps) }
            AnimatedVisibility(waitingToStart, enter = Motion.appear, exit = Motion.disappear) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.record_start_pending),
                        style = AppText.bodyS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = onCancelStart) { Text(stringResource(R.string.common_cancel)) }
                }
            }
            AnimatedVisibility(accuracyOff, enter = Motion.appear, exit = Motion.disappear) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.record_accuracy_off),
                        style = AppText.bodyS,
                        color = Warning,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = onFixAccuracy) { Text(stringResource(R.string.record_accuracy_fix)) }
                }
            }
            if (devDual) DevDualBadge()
            ControlButton(
                icon = Icons.Rounded.PlayArrow,
                label = stringResource(R.string.record_action_start),
                onClick = { haptics.confirm(); onStart() },
                containerColor = Accent,
                contentColor = OnAccent,
                enabled = startEnabled,
            )
        }
    }
}

@Composable
private fun GpsLine(gps: GpsStatus) {
    val hint = gpsHint(gps)
    val text = when (hint) {
        GpsHint.ACQUIRING -> stringResource(R.string.record_gps_acquiring)
        GpsHint.IMPROVING -> {
            val seconds = secondsUntilWeakStart(gps)
            if (seconds != null) stringResource(R.string.record_gps_improving_countdown, formatAccuracy(gps.accuracyM), seconds)
            else stringResource(R.string.record_gps_improving, formatAccuracy(gps.accuracyM))
        }
        GpsHint.READY -> stringResource(R.string.record_gps_ready, formatAccuracy(gps.accuracyM))
        GpsHint.WEAK -> stringResource(R.string.record_gps_weak, formatAccuracy(gps.accuracyM))
    }
    val dot by animateColorAsState(gps.quality.color(), Motion.state(), label = "gpsDot")
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(dot)
        Spacer(Modifier.width(Spacing.s))
        // Crossfade when the stage changes (acquiring -> improving -> ready), not on every countdown tick.
        AnimatedContent(
            targetState = hint to text,
            contentKey = { it.first },
            transitionSpec = { Motion.crossfade() },
            label = "gpsHint",
        ) { (h, t) ->
            Text(
                t,
                style = AppText.bodyS,
                color = if (h == GpsHint.WEAK) Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DevDualBadge() {
    Text(
        stringResource(R.string.dev_dual_engine_badge).uppercase(),
        style = AppText.eyebrow,
        color = TextTertiary,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(Raised2)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
    )
}

// ------------------------------------------------------------------------------------------ recording

@Composable
private fun RecordingContent(
    state: RecordingState.Recording,
    route: MapRoute?,
    userLocation: GeoPoint?,
    devDual: Boolean,
    locationOff: Boolean,
    onTurnOnLocation: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val stats = state.stats
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            EngineChip(engine = state.gps.engine, fallback = state.gps.fallbackActive, quality = state.gps.quality)
            if (devDual) DevDualBadge()
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(state.paused, enter = Motion.badgeIn, exit = Motion.badgeOut) {
                Text(
                    stringResource(R.string.record_paused).uppercase(),
                    style = AppText.eyebrow,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(Raised3)
                        .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            RouteMap(
                route = route,
                mode = RouteMapMode.LIVE,
                userLocation = userLocation,
                modifier = Modifier.fillMaxSize(),
            )
            // Recording carries on; GPS just gives no fixes until location is back on.
            LocationBanner(
                block = if (locationOff) LocationBlock.LOCATION_OFF else null,
                permissionAsked = false,
                onFix = onTurnOnLocation,
                modifier = Modifier.align(Alignment.TopCenter).padding(Spacing.gutter),
            )
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            StatTile(
                label = stringResource(R.string.stat_distance),
                value = formatDistanceKm(stats.distanceM),
                unit = stringResource(R.string.unit_km),
                size = StatSize.Hero,
                alignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            )
            val paceUnit = stringResource(R.string.unit_pace)
            StatRow(
                listOf(
                    StatItem(stringResource(R.string.stat_time), formatDuration(stats.activeMillis)),
                    StatItem(stringResource(R.string.stat_pace), formatPace(stats.currentPaceSecPerKm), paceUnit),
                    StatItem(stringResource(R.string.record_stat_avg_pace), formatPace(stats.avgPaceSecPerKm), paceUnit),
                ),
            )
            StatRow(
                listOf(
                    StatItem(stringResource(R.string.record_stat_last_km), formatPace(stats.lastSplitPaceSecPerKm), paceUnit),
                    StatItem(stringResource(R.string.record_stat_elevation), formatElevation(stats.elevationGainM)),
                    StatItem(stringResource(R.string.stat_cadence), formatCadence(stats.cadenceSpm), stringResource(R.string.unit_spm)),
                    StatItem(stringResource(R.string.stat_calories), formatCalories(stats.caloriesKcal), stringResource(R.string.unit_kcal)),
                ),
                size = StatSize.M,
            )
            AnimatedVisibility(state.gps.weakSignalWarning, enter = Motion.appear, exit = Motion.disappear) {
                Text(
                    stringResource(R.string.record_gps_weak_recording, formatAccuracy(state.gps.accuracyM)),
                    style = AppText.bodyS,
                    color = Warning,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            AnimatedContent(
                state.paused,
                transitionSpec = { Motion.crossfade() },
                contentAlignment = Alignment.Center,
                label = "recordControls",
            ) { paused ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxxl, Alignment.CenterHorizontally),
                ) {
                    if (paused) {
                        ControlButton(
                            icon = Icons.Rounded.PlayArrow,
                            label = stringResource(R.string.record_action_resume),
                            onClick = { haptics.light(); onResume() },
                            containerColor = Accent,
                            contentColor = OnAccent,
                        )
                        ControlButton(
                            icon = Icons.Rounded.Stop,
                            label = stringResource(R.string.record_action_finish),
                            onClick = { haptics.confirm(); onFinish() },
                            containerColor = Danger,
                            contentColor = Black,
                        )
                    } else {
                        ControlButton(
                            icon = Icons.Rounded.Pause,
                            label = stringResource(R.string.record_action_pause),
                            onClick = { haptics.light(); onPause() },
                            containerColor = Raised3,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        CircleIconButton(
            icon = icon,
            contentDescription = label,
            onClick = onClick,
            size = Sizes.recordButton,
            containerColor = containerColor,
            contentColor = contentColor,
            enabled = enabled,
        )
        Text(label.uppercase(), style = AppText.eyebrow, color = if (enabled) MaterialTheme.colorScheme.onSurface else TextTertiary)
    }
}

// ------------------------------------------------------------------------------------------ saving / recovery

@Composable
private fun SavingContent() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.record_saving), style = AppText.titleM, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RecoveryContent(
    state: RecordingState.NeedsRecoveryDecision,
    zoneId: String,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.record_recovery_title), style = AppText.titleL, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(R.string.record_recovery_body, formatRunDateTime(state.startedAtMillis, zoneId)),
            style = AppText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatTile(
            label = stringResource(R.string.stat_distance),
            value = formatDistanceKm(state.distanceM),
            unit = stringResource(R.string.unit_km),
            size = StatSize.XL,
        )
        Spacer(Modifier.size(Spacing.s))
        PrimaryButton(stringResource(R.string.record_recovery_resume), onResume, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
        SecondaryButton(stringResource(R.string.record_recovery_finish), onFinish, Modifier.fillMaxWidth())
    }
}
