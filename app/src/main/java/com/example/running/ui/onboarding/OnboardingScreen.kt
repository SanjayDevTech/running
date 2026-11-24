package com.example.running.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.running.R
import com.example.running.core.system.BatteryOptimization
import com.example.running.core.system.Permissions
import com.example.running.di.LocalAppContainer
import com.example.running.di.appViewModel
import com.example.running.setup.AndroidSetupStatusReader
import com.example.running.setup.OnboardingStep
import com.example.running.ui.components.PrimaryButton
import com.example.running.ui.settings.ProfileFields
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * CONTRACT(F). Public signature frozen (architecture.md §3.8). Profile, then permissions one page at
 * a time: fine location, background location (settings flow, skippable), notifications, activity
 * recognition, battery exemption, Google Location Accuracy. Nothing but the profile can block.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel<OnboardingViewModel> { c ->
        OnboardingViewModel(c.settings, c.setup.accuracyChecker, AndroidSetupStatusReader(c.core.app))
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.done) { if (state.done) onFinished() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.advance() }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.advance() }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.advance() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refresh() }
    val accuracyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.recheckAccuracy() }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.recheckAccuracy() }

    val step = state.step
    val granted = when (step) {
        OnboardingStep.LOCATION -> state.snapshot.fineLocation
        OnboardingStep.BACKGROUND_LOCATION -> state.snapshot.backgroundLocation
        OnboardingStep.NOTIFICATIONS -> state.snapshot.notifications
        OnboardingStep.ACTIVITY_RECOGNITION -> state.snapshot.activityRecognition
        OnboardingStep.BATTERY -> state.snapshot.batteryExempt
        else -> false
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(
            stringResource(R.string.onboarding_step_of, step.ordinal + 1, OnboardingStep.entries.size),
            style = AppText.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(stepIcon(step), contentDescription = null, tint = Accent, modifier = Modifier.size(40.dp))
        Text(stringResource(stepTitle(step)), style = AppText.titleL, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(stepBody(step)), style = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)

        when (step) {
            OnboardingStep.PROFILE -> {
                ProfileFields(
                    form = state.form, showErrors = state.showFormErrors,
                    onWeight = vm::setWeight, onAge = vm::setAge, onSex = vm::setSex, onHeight = vm::setHeight,
                )
                PrimaryButton(stringResource(R.string.onboarding_continue), vm::submitProfile, Modifier.fillMaxWidth())
            }
            OnboardingStep.ACCURACY -> {
                if (state.checkingAccuracy) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = Accent)
                    Text(stringResource(R.string.onboarding_accuracy_checking), style = AppText.bodyS)
                } else {
                    PrimaryButton(
                        stringResource(R.string.onboarding_accuracy_turn_on),
                        onClick = {
                            scope.launch {
                                val pending = vm.accuracyResolution()
                                val launched = pending != null &&
                                    runCatching { accuracyLauncher.launch(IntentSenderRequest.Builder(pending).build()) }.isSuccess
                                if (!launched) settingsLauncher.launch(Permissions.locationSettingsIntent())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SkipButton(stringResource(R.string.onboarding_skip)) { vm.advance() }
                }
            }
            else -> {
                if (granted) {
                    PrimaryButton(stringResource(R.string.onboarding_next), vm::advance, Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(
                        text = stringResource(if (step == OnboardingStep.BACKGROUND_LOCATION) R.string.onboarding_open_settings else R.string.onboarding_allow),
                        onClick = {
                            when (step) {
                                OnboardingStep.LOCATION -> locationLauncher.launch(Permissions.LOCATION)
                                // API 30+: the system opens this app's location settings page instead of a dialog.
                                OnboardingStep.BACKGROUND_LOCATION -> backgroundLauncher.launch(Permissions.BACKGROUND_LOCATION)
                                OnboardingStep.NOTIFICATIONS -> notificationLauncher.launch(Permissions.NOTIFICATIONS)
                                OnboardingStep.ACTIVITY_RECOGNITION -> activityLauncher.launch(Permissions.ACTIVITY_RECOGNITION)
                                OnboardingStep.BATTERY -> batteryLauncher.launch(BatteryOptimization.requestExemptionIntent(context))
                                else -> Unit
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SkipButton(stringResource(R.string.onboarding_skip)) {
                        if (step == OnboardingStep.BATTERY) vm.skipBattery() else vm.advance()
                    }
                }
            }
        }
        Spacer(Modifier.size(Spacing.s))
    }
}

@Composable
private fun SkipButton(text: String, onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth()) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun stepIcon(step: OnboardingStep): ImageVector = when (step) {
    OnboardingStep.PROFILE -> Icons.Rounded.Person
    OnboardingStep.LOCATION -> Icons.Rounded.LocationOn
    OnboardingStep.BACKGROUND_LOCATION -> Icons.Rounded.Autorenew
    OnboardingStep.NOTIFICATIONS -> Icons.Rounded.Notifications
    OnboardingStep.ACTIVITY_RECOGNITION -> Icons.Rounded.DirectionsWalk
    OnboardingStep.BATTERY -> Icons.Rounded.BatteryChargingFull
    OnboardingStep.ACCURACY -> Icons.Rounded.GpsFixed
}

private fun stepTitle(step: OnboardingStep): Int = when (step) {
    OnboardingStep.PROFILE -> R.string.onboarding_profile_title
    OnboardingStep.LOCATION -> R.string.onboarding_location_title
    OnboardingStep.BACKGROUND_LOCATION -> R.string.onboarding_background_title
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_title
    OnboardingStep.ACTIVITY_RECOGNITION -> R.string.onboarding_activity_title
    OnboardingStep.BATTERY -> R.string.onboarding_battery_title
    OnboardingStep.ACCURACY -> R.string.onboarding_accuracy_title
}

private fun stepBody(step: OnboardingStep): Int = when (step) {
    OnboardingStep.PROFILE -> R.string.onboarding_profile_body
    OnboardingStep.LOCATION -> R.string.onboarding_location_body
    OnboardingStep.BACKGROUND_LOCATION -> R.string.onboarding_background_body
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_body
    OnboardingStep.ACTIVITY_RECOGNITION -> R.string.onboarding_activity_body
    OnboardingStep.BATTERY -> R.string.onboarding_battery_body
    OnboardingStep.ACCURACY -> R.string.onboarding_accuracy_body
}
