package com.example.running.analysis

import com.example.running.testing.GpsFixture
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/**
 * Keeps the committed `b_*.json` fixtures identical to [AnalysisFixtures]. Every run writes fresh
 * copies to `app/build/generated-fixtures/`; after changing the generator, copy them to
 * `app/src/test/resources/fixtures/`.
 */
class AnalysisFixturesTest {
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    @Test fun committedFixturesMatchGenerator() {
        val outDir = File("build/generated-fixtures").apply { mkdirs() }
        val mismatched = mutableListOf<String>()
        for (spec in AnalysisFixtures.specs) {
            val generated = AnalysisFixtures.generate(spec)
            File(outDir, "${spec.name}.json").writeText(json.encodeToString(GpsFixture.serializer(), generated))
            val committed = runCatching { GpsFixture.load(spec.name) }.getOrNull()
            if (committed != generated) mismatched += spec.name
        }
        assertThat(mismatched).isEmpty()
    }

    @Test fun generatorIsDeterministic() {
        val spec = AnalysisFixtures.spec("b_run_walk_intervals")
        assertThat(AnalysisFixtures.generate(spec)).isEqualTo(AnalysisFixtures.generate(spec))
    }
}
