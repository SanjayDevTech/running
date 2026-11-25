package com.example.running.ui.settings

import androidx.compose.animation.AnimatedVisibility
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.running.R
import com.example.running.backup.BackupFile
import com.example.running.core.util.formatFileSize
import com.example.running.core.util.formatRunDateTime
import com.example.running.di.LocalAppContainer
import com.example.running.di.appViewModel
import com.example.running.setup.BackupIntervals
import com.example.running.ui.components.AppTopBar
import com.example.running.ui.components.ChoiceChips
import com.example.running.ui.components.PrimaryButton
import com.example.running.ui.components.SecondaryButton
import com.example.running.ui.components.SectionHeader
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Motion
import com.example.running.ui.theme.Danger
import com.example.running.ui.theme.Raised3
import com.example.running.ui.theme.Spacing

/** CONTRACT(F). Public signature frozen (architecture.md §3.8). */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val zone = container.core.clock.zoneId().id
    val vm = appViewModel<BackupViewModel> { c ->
        BackupViewModel(c.core.app, c.setup.backupManager, c.settings, c.setup.backupScheduler)
    }
    val ui by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reloadFiles() }

    // Restore waits for the user to choose between "runs only" and "runs and profile".
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::setFolder) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingRestore = it }

    val folder = ui.settings.backup.folderUri
    Column(Modifier.fillMaxSize().background(Black).navigationBarsPadding()) {
        AppTopBar(stringResource(R.string.backup_title), onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            SectionHeader(stringResource(R.string.backup_folder))
            SettingsCard {
                SettingsRow(
                    title = folder?.let(::folderLabel) ?: stringResource(R.string.backup_folder_default),
                    actionLabel = stringResource(R.string.backup_folder_choose),
                    onAction = { folderPicker.launch(null) },
                )
                if (folder != null) {
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.backup_folder_reset),
                        onClick = { vm.setFolder(null) },
                    )
                }
            }

            SectionHeader(stringResource(R.string.backup_schedule))
            val intervalLabels = BackupIntervals.OPTIONS.associateWith { backupIntervalLabel(it) }
            ChoiceChips(
                options = BackupIntervals.OPTIONS,
                selected = ui.settings.backup.intervalHours.takeIf { it in BackupIntervals.OPTIONS } ?: 0,
                label = { intervalLabels.getValue(it) }, onSelect = vm::setInterval,
            )
            Text(stringResource(R.string.backup_schedule_body), style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.settings.backup.lastBackupAtMillis?.let {
                Text(
                    stringResource(R.string.backup_last, formatRunDateTime(it, zone)),
                    style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrimaryButton(stringResource(R.string.backup_now), vm::backupNow, Modifier.fillMaxWidth().padding(top = Spacing.m), enabled = !ui.busy)
            SecondaryButton(
                stringResource(R.string.backup_export),
                { exportPicker.launch(com.example.running.backup.backupFileName(container.core.clock.nowMillis(), container.core.clock.zoneId())) },
                Modifier.fillMaxWidth(), enabled = !ui.busy,
            )
            SecondaryButton(
                stringResource(R.string.backup_restore),
                { restorePicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                Modifier.fillMaxWidth(), enabled = !ui.busy,
            )

            // Status lines open and fold in place (Motion.appear); the last message stays while it folds.
            var lastMessage by remember { mutableStateOf(ui.message) }
            ui.message?.let { lastMessage = it }
            AnimatedVisibility(ui.message != null, enter = Motion.appear, exit = Motion.disappear) {
                lastMessage?.let { m ->
                    Text(
                        messageText(m), style = AppText.body,
                        color = if (m is BackupMessage.Failed) Danger else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = Spacing.s),
                    )
                }
            }
            AnimatedVisibility(ui.busy, enter = Motion.appear, exit = Motion.disappear) {
                Text(stringResource(R.string.backup_working), style = AppText.bodyS)
            }

            SectionHeader(stringResource(R.string.backup_saved_backups))
            if (ui.files.isEmpty()) {
                Text(stringResource(R.string.backup_none), style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SettingsCard {
                    ui.files.forEachIndexed { i, file ->
                        if (i > 0) RowDivider()
                        BackupFileRow(file, zone, onRestore = { pendingRestore = file.uri }, onDelete = { vm.deleteFile(file) })
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Spacing.xxxl))
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = Raised3,
            title = { Text(stringResource(R.string.backup_restore_title), style = AppText.titleM) },
            text = { Text(stringResource(R.string.backup_restore_body), style = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton({ pendingRestore = null; vm.restore(uri, withProfile = true) }) {
                    Text(stringResource(R.string.backup_restore_with_profile), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                Column {
                    TextButton({ pendingRestore = null; vm.restore(uri, withProfile = false) }) {
                        Text(stringResource(R.string.backup_restore_runs_only), color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton({ pendingRestore = null }) {
                        Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
        )
    }
}

@Composable
private fun BackupFileRow(file: BackupFile, zone: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    SettingsRow(
        title = file.name,
        summary = stringResource(R.string.backup_file_summary, formatRunDateTime(file.modifiedMillis, zone), formatFileSize(file.sizeBytes)),
        onClick = onRestore,
        actionLabel = stringResource(R.string.common_delete),
        onAction = onDelete,
    )
}

@Composable
private fun backupIntervalLabel(hours: Int): String = when (val l = BackupIntervals.label(hours)) {
    BackupIntervals.Label.Off -> stringResource(R.string.backup_interval_off)
    BackupIntervals.Label.Daily -> stringResource(R.string.backup_interval_daily)
    BackupIntervals.Label.Weekly -> stringResource(R.string.backup_interval_weekly)
    is BackupIntervals.Label.EveryDays -> pluralStringResource(R.plurals.backup_interval_every_days, l.days, l.days)
    is BackupIntervals.Label.EveryHours -> pluralStringResource(R.plurals.backup_interval_every_hours, l.hours, l.hours)
}

@Composable
private fun messageText(m: BackupMessage): String = when (m) {
    is BackupMessage.Saved -> stringResource(R.string.backup_done_saved, m.name)
    is BackupMessage.Exported -> stringResource(R.string.backup_done_exported, m.runs)
    is BackupMessage.Restored -> stringResource(R.string.backup_done_restored, m.imported, m.skipped, m.failed)
    is BackupMessage.Failed -> stringResource(R.string.backup_failed, m.reason)
}



/** "primary:Documents/Backups" -> "Documents/Backups". */
private fun folderLabel(treeUri: String): String =
    Uri.parse(treeUri).lastPathSegment?.substringAfter(':')?.takeIf { it.isNotBlank() } ?: treeUri
