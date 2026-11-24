package com.example.running.backup

import android.net.Uri

/**
 * CONTRACT(F). The public methods below are frozen; F chooses the constructor parameters
 * (and updates SetupModule accordingly). Stub: every call fails, nothing is written.
 */
class BackupManager {
    suspend fun exportTo(target: Uri): Result<Int> = notBuilt()
    suspend fun importFrom(source: Uri): Result<ImportResult> = notBuilt()
    suspend fun saveScheduledBackup(): Result<String> = notBuilt()
    suspend fun listBackups(): List<BackupFile> = emptyList()

    private fun <T> notBuilt(): Result<T> = Result.failure(UnsupportedOperationException("Backup is not built yet"))
}

data class ImportResult(val imported: Int, val skippedExisting: Int, val failed: Int)

data class BackupFile(val name: String, val uri: Uri, val sizeBytes: Long, val modifiedMillis: Long)
