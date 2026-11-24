package com.example.running.map

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

class MapStyleTest {
    private val template: String by lazy {
        // Unit tests run with the module directory as working dir.
        File("src/main/assets/${MapStyle.ASSET_PATH}").readText()
    }

    @Test
    fun bundledStyleIsValidAndUsesOpenFreeMap() {
        val root = Json.parseToJsonElement(template).jsonObject
        assertThat(root.getValue("version").jsonPrimitive.content).isEqualTo("8")
        val source = root.getValue("sources").jsonObject.getValue(MapStyle.SOURCE_ID).jsonObject
        assertThat(source.getValue("url").jsonPrimitive.content).isEqualTo("https://tiles.openfreemap.org/planet")
        assertThat(source.getValue("attribution").jsonPrimitive.content).contains("OpenStreetMap")
    }

    @Test
    fun everyLayerHasUniqueIdAndKnownSource() {
        val layers = Json.parseToJsonElement(template).jsonObject.getValue("layers").jsonArray.map { it.jsonObject }
        val ids = layers.map { it.getValue("id").jsonPrimitive.content }
        assertThat(ids).containsNoDuplicates()
        layers.filter { it.getValue("type").jsonPrimitive.content != "background" }.forEach {
            assertThat(it.getValue("source").jsonPrimitive.content).isEqualTo(MapStyle.SOURCE_ID)
        }
    }

    @Test
    fun blankKeyKeepsTemplate() {
        assertThat(MapStyle.resolve(template, "")).isSameInstanceAs(template)
        assertThat(MapStyle.resolve(template, "   ")).isSameInstanceAs(template)
    }

    @Test
    fun mapTilerKeySwitchesSourceAndGlyphsButKeepsLayers() {
        val resolved = Json.parseToJsonElement(MapStyle.resolve(template, "abc123")).jsonObject
        val original = Json.parseToJsonElement(template).jsonObject
        val source = resolved.getValue("sources").jsonObject.getValue(MapStyle.SOURCE_ID).jsonObject
        assertThat(source.getValue("url").jsonPrimitive.content).isEqualTo("https://api.maptiler.com/tiles/v3-openmaptiles/tiles.json?key=abc123")
        assertThat(source.getValue("type").jsonPrimitive.content).isEqualTo("vector")
        assertThat(resolved.getValue("glyphs").jsonPrimitive.content).isEqualTo("https://api.maptiler.com/fonts/{fontstack}/{range}.pbf?key=abc123")
        assertThat(resolved.getValue("layers")).isEqualTo(original.getValue("layers"))
    }
}
