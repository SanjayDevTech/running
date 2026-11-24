package com.example.running.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Pure helpers for the bundled style (`assets/map/style_dark.json`, OpenFreeMap vector tiles).
 * With a MapTiler key the same layers are served from MapTiler instead (architecture.md §3.4).
 */
internal object MapStyle {
    const val ASSET_PATH = "map/style_dark.json"
    const val SOURCE_ID = "openmaptiles"

    private const val MAPTILER_TILES = "https://api.maptiler.com/tiles/v3-openmaptiles/tiles.json"
    private const val MAPTILER_GLYPHS = "https://api.maptiler.com/fonts/{fontstack}/{range}.pbf"
    private const val MAPTILER_ATTRIBUTION = "&copy; MapTiler &copy; OpenStreetMap contributors"

    private val json = Json { prettyPrint = false }

    /** Returns [template] unchanged for a blank key, otherwise with the vector source and glyphs pointed at MapTiler. */
    fun resolve(template: String, mapTilerKey: String): String {
        val key = mapTilerKey.trim()
        if (key.isEmpty()) return template
        val root = json.parseToJsonElement(template).jsonObject
        val sources = root.getValue("sources").jsonObject
        val oldSource = sources.getValue(SOURCE_ID).jsonObject
        val newSource = buildJsonObject {
            oldSource.forEach { (k, v) -> put(k, v) }
            put("url", "$MAPTILER_TILES?key=$key")
            put("attribution", MAPTILER_ATTRIBUTION)
        }
        val newSources = buildJsonObject {
            sources.forEach { (k, v) -> put(k, if (k == SOURCE_ID) newSource else v) }
        }
        val newRoot = JsonObject(
            root.toMutableMap().apply {
                put("sources", newSources)
                put("glyphs", JsonPrimitive("$MAPTILER_GLYPHS?key=$key"))
            },
        )
        return json.encodeToString(JsonObject.serializer(), newRoot)
    }
}
