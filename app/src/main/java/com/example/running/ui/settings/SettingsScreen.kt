package com.example.running.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.running.BuildConfig
import com.example.running.R
import com.example.running.core.contracts.AccuracyState
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.RecordingState
import com.example.running.core.system.BatteryOptimization
import com.example.running.core.system.Permissions
import com.example.running.core.util.formatRunDateTime
import com.example.running.core.util.formatWeightKg
import com.example.running.di.LocalAppContainer
import com.example.running.di.appViewModel
import com.example.running.setup.AndroidSetupStatusReader
import com.example.running.ui.components.ChoiceChips
import com.example.running.ui.components.DestructiveButton
import com.example.running.ui.components.ScreenTitle
import com.example.running.ui.components.SectionHeader
import com.example.running.ui.components.displayName
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Motion
import com.example.running.ui.theme.Spacing
import com.example.running.ui.theme.Success
import com.example.running.ui.theme.TextTertiary
import com.example.running.ui.theme.Warning
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** CONTRACT(F). Public signature frozen (architecture.md §3.8). */
@Composable
fun SettingsScreen(onOpenProfile: () -> Unit, onOpenBackup: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val vm = appViewModel<SettingsViewModel> { c ->
        SettingsViewModel(
            settings = c.settings,
            checker = c.setup.accuracyChecker,
            status = AndroidSetupStatusReader(c.core.app),
            recording = c.recording.controller.state.map { it is RecordingState.Recording || it is RecordingState.Saving },
            clock = c.core.clock,
        )
    }
    val ui by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val refreshLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refresh() }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val accuracyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.refresh() }

    val s = ui.settings
    val sys = ui.system
    Column(Modifier.fillMaxSize().background(Black)) {
        ScreenTitle(stringResource(R.string.settings_title))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            SettingsCard {
                SettingsRow(
                    title = stringResource(R.string.settings_profile),
                    summary = stringResource(
                        R.string.settings_profile_summary,
                        formatWeightKg(s.profile.weightKg),
                        s.profile.ageYears, s.profile.sex.label(),
                    ),
                    onClick = onOpenProfile,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_gps))
            SettingsCard {
                Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.settings_engine_title), style = AppText.body)
                    ChoiceChips(
                        options = LocationEngineMode.entries, selected = s.engineMode,
                        label = { it.displayName() }, onSelect = vm::setEngineMode,
                    )
                    Text(
                        stringResource(
                            if (ui.recording) R.string.settings_engine_locked else when (s.engineMode) {
                                LocationEngineMode.AUTO -> R.string.settings_engine_auto
                                LocationEngineMode.GOOGLE -> R.string.settings_engine_google
                                LocationEngineMode.BUILT_IN -> R.string.settings_engine_builtin
                            },
                        ),
                        style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_accuracy),
                    summary = when (ui.accuracy) {
                        AccuracyState.ON -> stringResource(R.string.settings_accuracy_on)
                        AccuracyState.OFF -> stringResource(R.string.settings_accuracy_off)
                        else -> stringResource(R.string.settings_accuracy_unknown)
                    },
                    statusColor = when (ui.accuracy) {
                        AccuracyState.ON -> Success
                        AccuracyState.OFF -> Warning
                        else -> TextTertiary
                    },
                    actionLabel = if (ui.accuracy == AccuracyState.OFF) stringResource(R.string.settings_accuracy_fix) else null,
                    onAction = {
                        scope.launch {
                            val pending = vm.accuracyResolution()
                            val launched = pending != null &&
                                runCatching { accuracyLauncher.launch(IntentSenderRequest.Builder(pending).build()) }.isSuccess
                            if (!launched) refreshLauncher.launch(Permissions.locationSettingsIntent())
                        }
                    },
                )
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_background),
                    summary = stringResource(
                        when {
                            sys.backgroundLocation -> R.string.settings_background_on
                            !sys.fineLocation -> R.string.settings_background_need_fine
                            else -> R.string.settings_background_off
                        },
                    ),
                    statusColor = if (sys.backgroundLocation) Success else Warning,
                    actionLabel = if (sys.canRequestBackground) stringResource(R.string.settings_grant) else null,
                    onAction = { backgroundLauncher.launch(Permissions.BACKGROUND_LOCATION) },
                )
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_battery),
                    summary = stringResource(if (sys.batteryExempt) R.string.settings_battery_on else R.string.settings_battery_off),
                    statusColor = if (sys.batteryExempt) Success else Warning,
                    actionLabel = if (!sys.batteryExempt) stringResource(R.string.settings_fix) else null,
                    onAction = { refreshLauncher.launch(BatteryOptimization.requestExemptionIntent(context)) },
                )
            }

            SectionHeader(stringResource(R.string.settings_section_data))
            SettingsCard {
                SettingsRow(
                    title = stringResource(R.string.settings_backup),
                    summary = s.backup.lastBackupAtMillis?.let {
                        stringResource(R.string.settings_backup_last, formatRunDateTime(it, container.core.clock.zoneId().id))
                    } ?: stringResource(R.string.settings_backup_never),
                    onClick = onOpenBackup,
                )
            }

            // Unlocked by the version taps, or turned off: the section opens and folds (Motion.appear).
            AnimatedVisibility(s.devModeUnlocked, enter = Motion.appear, exit = Motion.disappear) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SectionHeader(stringResource(R.string.settings_section_developer))
                    SettingsCard {
                        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            androidx.compose.foundation.layout.Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.settings_dev_dual), style = AppText.body)
                                    Text(
                                        stringResource(R.string.settings_dev_dual_body), style = AppText.bodyS,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = s.devDualEngine, onCheckedChange = vm::setDevDualEngine)
                            }
                            DestructiveButton(stringResource(R.string.settings_dev_disable), vm::disableDevMode)
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_section_about))
            SettingsCard {
                SettingsRow(
                    title = stringResource(R.string.settings_version),
                    summary = BuildConfig.VERSION_NAME + when (val h = ui.devHint) {
                        is DevHint.TapsLeft -> " · " + stringResource(R.string.settings_dev_taps_left, h.n)
                        DevHint.Enabled -> " · " + stringResource(R.string.settings_dev_enabled)
                        null -> ""
                    },
                    onClick = vm::tapVersion,
                )
            }
            Spacer32()
        }
    }
}

@Composable
private fun Spacer32() = androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Spacing.xxxl))
