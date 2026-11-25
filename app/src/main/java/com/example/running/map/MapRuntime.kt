package com.example.running.map

import android.content.Context
import com.example.running.BuildConfig
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager
import java.io.File

/**
 * Process-wide MapLibre state: SDK init, ambient cache size and the resolved style file.
 * [RouteMap] and the offline manager read the style from here, so the frozen composable signature
 * needs no module parameter.
 */
internal object MapRuntime {
    /** Ambient tile cache: tiles the user has looked at stay available offline until evicted. */
    const val AMBIENT_CACHE_BYTES = 150L * 1024 * 1024

    private const val STYLE_FILE = "map/style_resolved.json"

    @Volatile private var styleUri: String? = null
    @Volatile private var ambientConfigured = false

    /** Main thread. Idempotent; called on first use (RouteMap, offline manager), not at process start. */
    fun initialize(context: Context, mapTilerKey: String = BuildConfig.MAPTILER_KEY) {
        val app = context.applicationContext
        try {
            MapLibre.getInstance(app)
            if (!ambientConfigured) {
                ambientConfigured = true
                OfflineManager.getInstance(app).setMaximumAmbientCacheSize(AMBIENT_CACHE_BYTES, null)
            }
        } catch (_: UnsatisfiedLinkError) {
            // No native library (JVM unit tests / Robolectric): maps are simply unavailable there.
            return
        }
        styleUri(app, mapTilerKey)
    }

    /** `file://` URI of the style with the tile source resolved (OpenFreeMap, or MapTiler when a key is set). */
    fun styleUri(context: Context, mapTilerKey: String = BuildConfig.MAPTILER_KEY): String {
        styleUri?.let { return it }
        synchronized(this) {
            styleUri?.let { return it }
            val app = context.applicationContext
            val template = app.assets.open(MapStyle.ASSET_PATH).bufferedReader().use { it.readText() }
            val resolved = MapStyle.resolve(template, mapTilerKey)
            val file = File(app.filesDir, STYLE_FILE)
            file.parentFile?.mkdirs()
            if (!file.exists() || file.readText() != resolved) file.writeText(resolved)
            return "file://${file.absolutePath}".also { styleUri = it }
        }
    }
}
