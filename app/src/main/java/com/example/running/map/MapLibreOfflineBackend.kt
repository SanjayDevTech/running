package com.example.running.map

import android.content.Context
import com.example.running.core.model.GeoBounds
import com.example.running.core.util.AppDispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** MapLibre-backed [OfflineBackend]. All SDK calls run on the main thread (SDK requirement). */
internal class MapLibreOfflineBackend(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : OfflineBackend {
    /** Main thread. Starts MapLibre first if no map has been shown yet in this process. */
    private val manager: OfflineManager
        get() {
            MapRuntime.initialize(context)
            return OfflineManager.getInstance(context)
        }
    private val known = HashMap<Long, OfflineRegion>()

    override suspend fun find(runId: String): List<Long> = withContext(dispatchers.main) {
        val regions = suspendCancellableCoroutine { cont ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    cont.resume(offlineRegions?.toList().orEmpty())
                }

                override fun onError(error: String) = cont.resume(emptyList())
            })
        }
        regions.filter { OfflinePlanner.decodeRunId(it.metadata) == runId }.map { region ->
            known[region.id] = region
            region.id
        }
    }

    override suspend fun create(runId: String, bounds: GeoBounds): Long? = withContext(dispatchers.main) {
        val area = OfflinePlanner.area(bounds)
        val definition = OfflineTilePyramidRegionDefinition(
            MapRuntime.styleUri(context),
            LatLngBounds.from(area.maxLat, area.maxLon, area.minLat, area.minLon),
            OfflinePlanner.MIN_ZOOM,
            OfflinePlanner.MAX_ZOOM,
            context.resources.displayMetrics.density,
        )
        suspendCancellableCoroutine { cont ->
            manager.createOfflineRegion(
                definition,
                OfflinePlanner.encodeMetadata(runId),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        known[offlineRegion.id] = offlineRegion
                        cont.resume(offlineRegion.id)
                    }

                    override fun onError(error: String) = cont.resume(null)
                },
            )
        }
    }

    override suspend fun download(handle: Long): Boolean = withContext(dispatchers.main) {
        val region = known[handle] ?: return@withContext false
        val ok = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                fun finish(ok: Boolean) {
                    if (done.compareAndSet(false, true)) {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        region.setObserver(null) // drops the observer and the finished continuation it holds
                        cont.resume(ok)
                    }
                }
                region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        if (status.isComplete) finish(true)
                    }

                    override fun onError(error: OfflineRegionError) = finish(false)
                    override fun mapboxTileCountLimitExceeded(limit: Long) = finish(false)
                })
                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                cont.invokeOnCancellation {
                    if (done.compareAndSet(false, true)) {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        region.setObserver(null)
                    }
                }
            }
        } ?: run {
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.setObserver(null)
            false
        }
        // A finished region needs no handle any more (find() looks it up again); a failed one is
        // still deleted through it by the caller.
        if (ok) known.remove(handle)
        ok
    }

    override suspend fun progress(handle: Long): RegionProgress? = withContext(dispatchers.main) {
        // A status check ends this lookup: drop the native handle (find() re-adds it when needed).
        val region = known.remove(handle) ?: return@withContext null
        suspendCancellableCoroutine { cont ->
            region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) = cont.resume(status?.let { RegionProgress(it.isComplete) })
                override fun onError(error: String?) = cont.resume(null)
            })
        }
    }

    override suspend fun delete(handle: Long) {
        withContext(dispatchers.main) {
            val region = known.remove(handle) ?: return@withContext
            suspendCancellableCoroutine { cont ->
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() = cont.resume(Unit)
                    override fun onError(error: String) = cont.resume(Unit)
                })
            }
        }
    }

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
