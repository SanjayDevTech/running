package com.example.running.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

/**
 * Where backup bytes live. The only SAF / MediaStore code in the app (track F owns it).
 * [folderUri] null = `Download/Running/` via MediaStore, otherwise a SAF tree uri picked by the user.
 */
internal interface BackupStorage {
    fun <T> readUri(uri: Uri, block: (InputStream) -> T): T
    fun writeUri(uri: Uri, block: (OutputStream) -> Unit)
    fun saveNew(folderUri: String?, name: String, block: (OutputStream) -> Unit): BackupFile
    fun list(folderUri: String?): List<BackupFile>
    fun delete(file: BackupFile): Boolean
}

internal class AndroidBackupStorage(private val context: Context) : BackupStorage {
    private val resolver get() = context.contentResolver

    override fun <T> readUri(uri: Uri, block: (InputStream) -> T): T =
        (resolver.openInputStream(uri) ?: error("Cannot read $uri")).use(block)

    override fun writeUri(uri: Uri, block: (OutputStream) -> Unit) {
        (resolver.openOutputStream(uri, "wt") ?: error("Cannot write $uri")).use(block)
    }

    override fun saveNew(folderUri: String?, name: String, block: (OutputStream) -> Unit): BackupFile =
        if (folderUri != null) saveToFolder(Uri.parse(folderUri), name, block) else saveToDownloads(name, block)

    private fun saveToFolder(tree: Uri, name: String, block: (OutputStream) -> Unit): BackupFile {
        val dir = DocumentFile.fromTreeUri(context, tree)?.takeIf { it.canWrite() }
            ?: error("The backup folder is no longer accessible. Pick it again in Backup settings.")
        val file = dir.createFile(MIME, name) ?: error("Could not create $name")
        try {
            writeUri(file.uri, block)
        } catch (e: Exception) {
            runCatching { file.delete() }
            throw e
        }
        return BackupFile(file.name ?: name, file.uri, file.length(), file.lastModified())
    }

    private fun saveToDownloads(name: String, block: (OutputStream) -> Unit): BackupFile {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create $name")
        try {
            writeUri(uri, block)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        val size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        return BackupFile(name, uri, size, System.currentTimeMillis())
    }

    override fun list(folderUri: String?): List<BackupFile> =
        (if (folderUri != null) listFolder(Uri.parse(folderUri)) else listDownloads())
            .sortedByDescending { it.modifiedMillis }

    private fun listFolder(tree: Uri): List<BackupFile> {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
        return dir.listFiles()
            .filter { it.isFile && isBackupName(it.name.orEmpty()) }
            .map { BackupFile(it.name.orEmpty(), it.uri, it.length(), it.lastModified()) }
    }

    private fun listDownloads(): List<BackupFile> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val out = mutableListOf<BackupFile>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(RELATIVE_DIR), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1).orEmpty()
                if (!isBackupName(name)) continue
                val uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
                out += BackupFile(name, uri, c.getLong(2), c.getLong(3) * 1000L)
            }
        }
        return out
    }

    override fun delete(file: BackupFile): Boolean =
        if (DocumentsContract.isDocumentUri(context, file.uri)) {
            DocumentFile.fromSingleUri(context, file.uri)?.delete() == true
        } else {
            resolver.delete(file.uri, null, null) > 0
        }

    companion object {
        const val MIME = "application/json"
        val RELATIVE_DIR: String = Environment.DIRECTORY_DOWNLOADS + "/Running/"
    }
}

/** `running-backup-yyyyMMdd-HHmmss.json`, possibly with a de-duplicating suffix added by SAF. */
internal fun isBackupName(name: String): Boolean = name.startsWith("running-backup-") && name.contains(".json")
