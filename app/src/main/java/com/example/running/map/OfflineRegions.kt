package com.example.running.map

import com.example.running.core.contracts.MapOfflineManager
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.OfflineAreaStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Progress of one stored region, independent of the MapLibre classes. */
internal data class RegionProgress(val complete: Boolean)

/** Thin seam over MapLibre's OfflineManager so the bookkeeping is unit-testable. */
internal interface OfflineBackend {
    /** Handles of every stored region whose metadata names [runId]. */
    suspend fun find(runId: String): List<Long>
    suspend fun create(runId: String, bounds: GeoBounds): Long?
    /** Starts the download and suspends until it is complete (true) or failed (false). */
    suspend fun download(handle: Long): Boolean
    suspend fun progress(handle: Long): RegionProgress?
    suspend fun delete(handle: Long)
}

/**
 * One offline region per saved run (metadata stores the runId). Re-downloading a run replaces its
 * region. Failed or unfinished regions report FAILED so the caller can retry.
 */
internal class RegionOfflineManager(private val backend: OfflineBackend) : MapOfflineManager {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val mutex = Mutex()

    override suspend fun downloadRunArea(runId: String, bounds: GeoBounds): Boolean {
        if (!inFlight.add(runId)) return false
        failed.remove(runId)
        var ok = false
        try {
            mutex.withLock {
                backend.find(runId).forEach { backend.delete(it) }
                val handle = backend.create(runId, bounds)
                ok = handle != null && backend.download(handle)
                if (!ok && handle != null) backend.delete(handle)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ok = false
        } finally {
            if (!ok) failed.add(runId)
            inFlight.remove(runId)
        }
        return ok
    }

    override suspend fun deleteRunArea(runId: String) {
        failed.remove(runId)
        mutex.withLock { backend.find(runId).forEach { backend.delete(it) } }
    }

    override suspend fun status(runId: String): OfflineAreaStatus {
        if (runId in inFlight) return OfflineAreaStatus.DOWNLOADING
        val handle = backend.find(runId).firstOrNull()
            ?: return if (runId in failed) OfflineAreaStatus.FAILED else OfflineAreaStatus.NONE
        val complete = backend.progress(handle)?.complete == true
        return if (complete) OfflineAreaStatus.READY else OfflineAreaStatus.FAILED
    }
}

internal object OfflinePlanner {
    /** Extra margin around the run so panning a little past the route still has tiles. */
    const val PAD_METERS = 600.0
    const val MIN_ZOOM = 11.0
    const val MAX_ZOOM = 16.0

    fun area(bounds: GeoBounds): GeoBounds = bounds.padded(PAD_METERS)

    private val runIdPattern = Regex("\"runId\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    fun encodeMetadata(runId: String): ByteArray =
        "{\"runId\":\"${runId.replace("\\", "\\\\").replace("\"", "\\\"")}\"}".toByteArray(Charsets.UTF_8)

    fun decodeRunId(metadata: ByteArray?): String? {
        if (metadata == null) return null
        val raw = runIdPattern.find(String(metadata, Charsets.UTF_8))?.groupValues?.get(1) ?: return null
        return raw.replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
