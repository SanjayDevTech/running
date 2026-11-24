package com.example.running.backup

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** In-memory [BackupStorage]; one "folder" per folderUri key (null = Downloads). Needs Robolectric for Uri. */
internal class MemStorage : BackupStorage {
    val blobs = mutableMapOf<Uri, ByteArray>()
    private val meta = mutableMapOf<Uri, BackupFile>()
    private val folders = mutableMapOf<Uri, String?>()
    private var tick = 0L
    var failWrites = false

    override fun <T> readUri(uri: Uri, block: (InputStream) -> T): T =
        block(ByteArrayInputStream(blobs[uri] ?: error("missing $uri")))

    override fun writeUri(uri: Uri, block: (OutputStream) -> Unit) {
        if (failWrites) error("disk full")
        val out = ByteArrayOutputStream()
        block(out)
        blobs[uri] = out.toByteArray()
    }

    override fun saveNew(folderUri: String?, name: String, block: (OutputStream) -> Unit): BackupFile {
        val uri = Uri.parse("mem://${folderUri ?: "downloads"}/$name")
        writeUri(uri, block)
        val file = BackupFile(name, uri, blobs.getValue(uri).size.toLong(), ++tick)
        meta[uri] = file
        folders[uri] = folderUri
        return file
    }

    override fun list(folderUri: String?): List<BackupFile> =
        meta.values.filter { folders[it.uri] == folderUri }.sortedByDescending { it.modifiedMillis }

    override fun delete(file: BackupFile): Boolean {
        blobs.remove(file.uri)
        return meta.remove(file.uri) != null
    }
}
