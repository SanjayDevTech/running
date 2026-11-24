package com.example.running.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.running.backup.BackupFile
import com.example.running.backup.BackupManager
import com.example.running.backup.BackupScheduler
import com.example.running.core.model.AppSettings
import com.example.running.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Outcome line shown under the buttons. */
internal sealed interface BackupMessage {
    data class Saved(val name: String) : BackupMessage
    data class Exported(val runs: Int) : BackupMessage
    data class Restored(val imported: Int, val skipped: Int, val failed: Int) : BackupMessage
    data class Failed(val reason: String) : BackupMessage
}

internal data class BackupUiState(
    val settings: AppSettings = AppSettings(),
    val files: List<BackupFile> = emptyList(),
    val busy: Boolean = false,
    val message: BackupMessage? = null,
)

internal class BackupViewModel(
    private val context: Context,
    private val manager: BackupManager,
    private val settings: SettingsRepository,
    private val scheduler: BackupScheduler,
) : ViewModel() {
    private val files = MutableStateFlow<List<BackupFile>>(emptyList())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<BackupMessage?>(null)

    val state: StateFlow<BackupUiState> = combine(settings.settings, files, busy, message) { s, f, b, m ->
        BackupUiState(s, f, b, m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    init {
        reloadFiles()
    }

    fun reloadFiles() {
        viewModelScope.launch { files.value = manager.listBackups() }
    }

    fun setInterval(hours: Int) {
        viewModelScope.launch {
            settings.setBackupIntervalHours(hours)
            scheduler.apply(hours)
        }
    }

    fun setFolder(uri: Uri?) {
        viewModelScope.launch {
            manager.setFolder(context, uri)
            files.value = manager.listBackups()
        }
    }

    fun backupNow() = launchTask {
        manager.saveScheduledBackup().fold({ BackupMessage.Saved(it) }, { BackupMessage.Failed(it.message.orEmpty()) })
    }

    fun export(target: Uri) = launchTask {
        manager.exportTo(target).fold({ BackupMessage.Exported(it) }, { BackupMessage.Failed(it.message.orEmpty()) })
    }

    fun restore(source: Uri, withProfile: Boolean) = launchTask {
        manager.importFrom(source, withProfile).fold(
            { BackupMessage.Restored(it.imported, it.skippedExisting, it.failed) },
            { BackupMessage.Failed(it.message.orEmpty()) },
        )
    }

    fun deleteFile(file: BackupFile) {
        viewModelScope.launch {
            manager.deleteBackup(file)
            files.value = manager.listBackups()
        }
    }

    fun dismissMessage() = message.update { null }

    private fun launchTask(block: suspend () -> BackupMessage) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            message.value = block()
            files.value = manager.listBackups()
            busy.value = false
        }
    }
}
