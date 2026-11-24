package com.example.running.ui.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.running.R
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.GpsQuality
import com.example.running.core.model.GpsStatus
import com.example.running.core.model.MapRoute
import com.example.running.core.model.RecordingEvent
import com.example.running.core.model.RecordingState
import com.example.running.core.model.RouteMapMode
import com.example.running.core.system.Permissions
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
import com.example.running.ui.components.EmptyState
import com.example.running.ui.components.EngineChip
import com.example.running.ui.components.PrimaryButton
import com.example.running.ui.components.SecondaryButton
import com.example.running.ui.components.StatItem
import com.example.running.ui.components.StatRow
import com.example.running.ui.components.StatSize
import com.example.running.ui.components.StatTile
import com.example.running.ui.components.displayName
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.Danger
import com.example.running.ui.theme.OnAccent
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.Radius
import com.example.running.ui.theme.Raised2
import com.example.running.ui.theme.Raised3
import com.example.running.ui.theme.Sizes
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.Success
import com.example.running.ui.theme.TextPrimary
import com.example.running.ui.theme.TextSecondary
import com.example.running.ui.theme.TextTertiary
import com.example.running.ui.theme.Warning
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
    var locationOn by remember { mutableStateOf(Permissions.isLocationEnabled(context)) }
    var locationAsked by rememberSaveable { mutableStateOf(false) }
    var startPermsAsked by rememberSaveable { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        hasLocation = Permissions.hasFineLocation(context)
        locationOn = Permissions.isLocationEnabled(context)
        vm.refreshAccuracy()
        onPauseOrDispose { }
    }
    LifecycleStartEffect(Unit) {
        visible = true
        onStopOrDispose {
            visible = false
            vm.endWarmUp()
        }
    }
    val canWarmUp = hasLocation && locationOn
    val isIdle = state is RecordingState.Idle
    LaunchedEffect(isIdle, canWarmUp, visible) {
        if (isIdle && canWarmUp && visible) vm.beginWarmUp()
    }
    LaunchedEffect(canWarmUp, state is RecordingState.WarmingUp) {
        if (!canWarmUp && state is RecordingState.WarmingUp) vm.endWarmUp()
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = Permissions.hasFineLocation(context)
        locationAsked = true
    }
    // Notifications (live stats) and activity recognition (steps) are asked once, right before the first start.
    val startPermsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.start()
    }
    val accuracyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.refreshAccuracy()
    }

    val onStart: () -> Unit = {
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
    val onFixAccuracy: () -> Unit = {
        scope.launch {
            vm.accuracyResolution()?.let { accuracyLauncher.launch(IntentSenderRequest.Builder(it).build()) }
        }
    }

    Box(Modifier.fillMaxSize().background(Black)) {
        when (val s = state) {
            is RecordingState.Recording -> RecordingContent(
                state = s,
                route = route,
                userLocation = userLocation,
                devDual = devDual,
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
            is RecordingState.Idle, is RecordingState.WarmingUp -> when {
                !hasLocation -> EmptyState(
                    title = stringResource(R.string.record_permission_title),
                    body = stringResource(R.string.record_permission_body),
                    actionLabel = stringResource(if (locationAsked) R.string.record_permission_open_settings else R.string.record_permission_allow),
                    onAction = {
                        if (locationAsked) context.startActivity(Permissions.appSettingsIntent(context))
                        else locationLauncher.launch(Permissions.LOCATION)
                    },
                    modifier = Modifier.statusBarsPadding(),
                )
                !locationOn -> EmptyState(
                    title = stringResource(R.string.record_location_off_title),
                    body = stringResource(R.string.record_location_off_body),
                    actionLabel = stringResource(R.string.record_location_off_action),
                    onAction = { context.startActivity(Permissions.locationSettingsIntent()) },
                    modifier = Modifier.statusBarsPadding(),
                )
                else -> ReadyContent(
                    gps = (s as? RecordingState.WarmingUp)?.gps ?: GpsStatus.NONE,
                    userLocation = userLocation,
                    accuracyOff = accuracyOff,
                    devDual = devDual,
                    onStart = onStart,
                    onFixAccuracy = onFixAccuracy,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.gutter))
    }
}

// ------------------------------------------------------------------------------------------ before start

@Composable
private fun ReadyContent(
    gps: GpsStatus,
    userLocation: GeoPoint?,
    accuracyOff: Boolean,
    devDual: Boolean,
    onStart: () -> Unit,
    onFixAccuracy: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.record_title), style = AppText.titleL, color = TextPrimary, modifier = Modifier.weight(1f))
            EngineChip(engine = gps.engine, fallback = gps.fallbackActive, quality = gps.quality)
        }
        RouteMap(
            route = null,
            mode = RouteMapMode.LIVE,
            userLocation = userLocation,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            GpsLine(gps)
            if (accuracyOff) {
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
                onClick = onStart,
                containerColor = Accent,
                contentColor = OnAccent,
                enabled = gps.startAllowed,
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
    val dot = when (gps.quality) {
        GpsQuality.GOOD -> Success
        GpsQuality.FAIR, GpsQuality.POOR -> Warning
        GpsQuality.NONE -> TextTertiary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(Spacing.s))
        Text(
            text,
            style = AppText.bodyS,
            color = if (hint == GpsHint.WEAK) Warning else TextSecondary,
            textAlign = TextAlign.Center,
        )
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
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
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
            if (state.paused) {
                Text(
                    stringResource(R.string.record_paused).uppercase(),
                    style = AppText.eyebrow,
                    color = TextPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(Raised3)
                        .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                )
            }
        }
        RouteMap(
            route = route,
            mode = RouteMapMode.LIVE,
            userLocation = userLocation,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            StatTile(
                label = stringResource(R.string.record_stat_distance),
                value = formatDistanceKm(stats.distanceM),
                unit = stringResource(R.string.record_unit_km),
                size = StatSize.Hero,
                alignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            )
            val paceUnit = stringResource(R.string.record_unit_pace)
            StatRow(
                listOf(
                    StatItem(stringResource(R.string.record_stat_time), formatDuration(stats.movingMillis)),
                    StatItem(stringResource(R.string.record_stat_pace), formatPace(stats.currentPaceSecPerKm), paceUnit),
                    StatItem(stringResource(R.string.record_stat_avg_pace), formatPace(stats.avgPaceSecPerKm), paceUnit),
                ),
            )
            StatRow(
                listOf(
                    StatItem(stringResource(R.string.record_stat_last_km), formatPace(stats.lastSplitPaceSecPerKm), paceUnit),
                    StatItem(stringResource(R.string.record_stat_elevation), formatElevation(stats.elevationGainM)),
                    StatItem(stringResource(R.string.record_stat_cadence), formatCadence(stats.cadenceSpm), stringResource(R.string.record_unit_spm)),
                    StatItem(stringResource(R.string.record_stat_calories), formatCalories(stats.caloriesKcal), stringResource(R.string.record_unit_kcal)),
                ),
                size = StatSize.M,
            )
            if (state.gps.weakSignalWarning) {
                Text(
                    stringResource(R.string.record_gps_weak_recording, formatAccuracy(state.gps.accuracyM)),
                    style = AppText.bodyS,
                    color = Warning,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxxl, Alignment.CenterHorizontally),
            ) {
                if (state.paused) {
                    ControlButton(
                        icon = Icons.Rounded.PlayArrow,
                        label = stringResource(R.string.record_action_resume),
                        onClick = onResume,
                        containerColor = Accent,
                        contentColor = OnAccent,
                    )
                    ControlButton(
                        icon = Icons.Rounded.Stop,
                        label = stringResource(R.string.record_action_finish),
                        onClick = onFinish,
                        containerColor = Danger,
                        contentColor = Black,
                    )
                } else {
                    ControlButton(
                        icon = Icons.Rounded.Pause,
                        label = stringResource(R.string.record_action_pause),
                        onClick = onPause,
                        containerColor = Raised3,
                        contentColor = TextPrimary,
                    )
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
        Text(label.uppercase(), style = AppText.eyebrow, color = if (enabled) TextPrimary else TextTertiary)
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
        CircularProgressIndicator(color = TextPrimary)
        Text(stringResource(R.string.record_saving), style = AppText.titleM, color = TextPrimary)
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
        Text(stringResource(R.string.record_recovery_title), style = AppText.titleL, color = TextPrimary)
        Text(
            stringResource(R.string.record_recovery_body, formatRunDateTime(state.startedAtMillis, zoneId)),
            style = AppText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatTile(
            label = stringResource(R.string.record_stat_distance),
            value = formatDistanceKm(state.distanceM),
            unit = stringResource(R.string.record_unit_km),
            size = StatSize.XL,
        )
        Spacer(Modifier.size(Spacing.s))
        PrimaryButton(stringResource(R.string.record_recovery_resume), onResume, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
        SecondaryButton(stringResource(R.string.record_recovery_finish), onFinish, Modifier.fillMaxWidth())
    }
}
