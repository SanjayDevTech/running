package com.example.running.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.RunStatus
import com.example.running.core.util.AppDispatchers
import com.example.running.core.util.Clock
import com.example.running.data.repo.RunRepository
import com.example.running.data.settings.SettingsRepository
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * CONTRACT(F). The five public methods of §3.5 are frozen; the constructor is F's choice.
 * JSON backup / restore of finished runs, profile and engine mode. Restore is idempotent:
 * run ids that already exist are skipped.
 */
class BackupManager internal constructor(
    private val storage: BackupStorage,
    private val runs: RunRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val appVersion: String,
) {
    constructor(
        context: Context,
        runs: RunRepository,
        settings: SettingsRepository,
        clock: Clock,
        dispatchers: AppDispatchers,
        appVersion: String,
    ) : this(AndroidBackupStorage(context.applicationContext), runs, settings, clock, dispatchers, appVersion)

    /** Writes every finished run to a user-picked file. Returns the number of runs written. */
    suspend fun exportTo(target: Uri): Result<Int> = guarded {
        val payload = buildPayload()
        withContext(dispatchers.io) { storage.writeUri(target) { writePayload(it, payload) } }
        payload.runs.size
    }

    /** Restores runs only, plus profile and engine mode when onboarding has not been completed. */
    suspend fun importFrom(source: Uri): Result<ImportResult> =
        importFrom(source, restoreProfile = !settings.current().onboardingComplete)

    /** As [importFrom], with the caller deciding whether profile and engine mode are restored too. */
    suspend fun importFrom(source: Uri, restoreProfile: Boolean): Result<ImportResult> = guarded {
        val payload = withContext(dispatchers.io) { storage.readUri(source) { readPayload(it) } }
        restore(payload, restoreProfile)
    }

    /**
     * Writes a timestamped file to the backup folder (or Download/Running) and prunes to the newest 5.
     * Always writes ("Back up now"); the periodic job uses [saveScheduledBackupIfChanged].
     */
    suspend fun saveScheduledBackup(): Result<String> = guarded { saveBackup(changeFingerprint()) }

    /**
     * The periodic job: as [saveScheduledBackup], but skipped (success with null) when nothing that goes
     * into a backup changed since the last one and that backup's folder still holds a backup file.
     */
    suspend fun saveScheduledBackupIfChanged(): Result<String?> = guarded {
        val fingerprint = changeFingerprint()
        val backup = settings.current().backup
        val unchanged = backup.lastBackupFingerprint == fingerprint &&
            withContext(dispatchers.io) { runCatching { storage.list(backup.folderUri) }.getOrDefault(emptyList()) }.isNotEmpty()
        if (unchanged) null else saveBackup(fingerprint)
    }

    private suspend fun saveBackup(fingerprint: String): String {
        val payload = buildPayload()
        val folder = settings.current().backup.folderUri
        val name = backupFileName(clock.nowMillis(), clock.zoneId())
        val saved = withContext(dispatchers.io) { storage.saveNew(folder, name) { writePayload(it, payload) } }
        settings.setLastBackupAt(clock.nowMillis())
        settings.setLastBackupFingerprint(fingerprint)
        runCatching {
            withContext(dispatchers.io) {
                filesToPrune(storage.list(folder), KEEP_BACKUPS).forEach { storage.delete(it) }
            }
        }
        return saved.name
    }

    /**
     * Cheap summary of everything a backup holds, from the run rows only (no points): which finished runs
     * exist (added, deleted, imported) and each one's edit time (rename, type, re-analysis all bump
     * updatedAt), the profile and the engine mode. Taken before the payload is built, so a change made
     * while writing makes the next backup run again.
     */
    internal suspend fun changeFingerprint(): String {
        val finished = runs.observeRuns(ActivityFilter.ALL).first()
        val current = settings.current()
        // Every run's own edit time, not just the newest: an imported run can carry a later one.
        val runsKey = finished.map { "${it.id}:${it.updatedAtMillis}" }.sorted()
        return listOf(
            "v1", finished.size, runsKey.hashCode(), current.profile.toSnapshot().hashCode(), current.engineMode.name,
        ).joinToString("|")
    }

    /** Backups in the current destination, newest first. */
    suspend fun listBackups(): List<BackupFile> = withContext(dispatchers.io) {
        runCatching { storage.list(settings.current().backup.folderUri) }.getOrDefault(emptyList())
    }

    /** Deletes one listed backup. */
    suspend fun deleteBackup(file: BackupFile): Boolean = withContext(dispatchers.io) {
        runCatching { storage.delete(file) }.getOrDefault(false)
    }

    /**
     * Sets (or clears, with null) the SAF backup folder. Persists read/write access to the new tree
     * and releases the old one. Does nothing to the stored files.
     */
    suspend fun setFolder(context: Context, treeUri: Uri?) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        settings.current().backup.folderUri?.let { old ->
            if (old != treeUri?.toString()) runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        if (treeUri != null) runCatching { resolver.takePersistableUriPermission(treeUri, flags) }
        settings.setBackupFolderUri(treeUri?.toString())
    }

    // ---- internals ----------------------------------------------------------------------

    internal suspend fun buildPayload(): BackupPayload = withContext(dispatchers.io) {
        val current = settings.current()
        val graphs = runs.getAllRunIds().mapNotNull { runs.getRunGraph(it) }
            .filter { it.run.status == RunStatus.FINISHED }
            .sortedBy { it.run.startedAtMillis }
        BackupPayload(
            createdAtMillis = clock.nowMillis(),
            appVersion = appVersion,
            profile = current.profile.toSnapshot(),
            settings = SettingsSnapshot(engineMode = current.engineMode.name),
            runs = graphs.map { it.toSnapshot() },
        )
    }

    internal suspend fun restore(payload: BackupPayload, restoreProfile: Boolean): ImportResult {
        var imported = 0
        var skipped = 0
        var failed = 0
        for (snapshot in payload.runs) {
            try {
                if (runs.importRunGraph(snapshot.toGraph())) imported++ else skipped++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed++
            }
        }
        if (restoreProfile) {
            val profile = payload.profile.toDomain()
            settings.updateProfile(profile)
            settings.setLearnedStrides(profile.learnedRunStrideM, profile.learnedWalkStrideM)
            runCatching { com.example.running.core.model.LocationEngineMode.valueOf(payload.settings.engineMode) }
                .getOrNull()?.let { settings.setEngineMode(it) }
        }
        return ImportResult(imported, skipped, failed)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writePayload(out: java.io.OutputStream, payload: BackupPayload) =
        BackupJson.encodeToStream(BackupPayload.serializer(), payload, out)

    @OptIn(ExperimentalSerializationApi::class)
    private fun readPayload(input: java.io.InputStream): BackupPayload {
        val payload = BackupJson.decodeFromStream(BackupPayload.serializer(), input)
        require(payload.schemaVersion in 1..SUPPORTED_SCHEMA) { "Unsupported backup version ${payload.schemaVersion}" }
        return payload
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        const val KEEP_BACKUPS = 5
        const val SUPPORTED_SCHEMA = 1
    }
}

data class ImportResult(val imported: Int, val skippedExisting: Int, val failed: Int)

data class BackupFile(val name: String, val uri: Uri, val sizeBytes: Long, val modifiedMillis: Long)

/** `running-backup-yyyyMMdd-HHmmss.json` in the given zone. */
internal fun backupFileName(millis: Long, zone: java.time.ZoneId): String =
    "running-backup-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).format(Instant.ofEpochMilli(millis).atZone(zone)) + ".json"

/** Everything beyond the newest [keep] files (by modified time, then name). */
internal fun filesToPrune(files: List<BackupFile>, keep: Int): List<BackupFile> =
    files.sortedWith(compareByDescending<BackupFile> { it.modifiedMillis }.thenByDescending { it.name }).drop(keep)
