package com.example.running.analysis

import com.example.running.core.model.AnalysisInput
import com.example.running.testing.GpsFixture
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.security.MessageDigest

/**
 * Pins [DefaultRunAnalyzer]'s exact output on every `b_` fixture (plus all-weak-GPS variants, which
 * step-fill almost every interval) to fingerprints taken with the implementation before the
 * local-stride window became a binary search (2026-11-25). A pure speed-up must keep them bit-identical.
 */
class AnalyzerEquivalenceTest {
    private val analyzer = DefaultRunAnalyzer(DefaultRunNamer())

    private val fixtures = listOf(
        "b_city_loop_5k", "b_hill_baro", "b_walk_mostly", "b_run_walk_intervals",
        "b_tunnel_stepfill", "b_paused", "b_stationary_table", "b_pb_10k",
    )

    private fun inputs(): Map<String, AnalysisInput> = fixtures.flatMap { name ->
        val input = GpsFixture.load(name).toAnalysisInput()
        val weak = input.copy(rawPoints = input.rawPoints.map { it.copy(horizontalAccuracyM = 28f) })
        listOf(name to input, "$name/weak" to weak)
    }.toMap()

    private fun fingerprint(input: AnalysisInput): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(analyzer.analyze(input).toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    @Test fun outputIsBitIdenticalToThePreviousImplementation() {
        val actual = inputs().mapValues { fingerprint(it.value) }
        for ((name, expected) in GOLDEN) {
            assertWithMessage(name).that(actual[name]).isEqualTo(expected)
        }
        assertWithMessage("every input pinned").that(actual.keys).isEqualTo(GOLDEN.keys)
    }

    @Test fun strideWindowMatchesTheLinearScan() {
        val rnd = java.util.Random(3)
        repeat(200) {
            val n = rnd.nextInt(60)
            var t = 0L
            val times = LongArray(n) { t += rnd.nextInt(3) * 500L; t } // repeated timestamps included
            val lo = rnd.nextInt(40_000).toLong() - 5_000
            val hi = lo + rnd.nextInt(30_000)
            val expected = (1 until n).filter { k -> !(times[k] < lo || times[k - 1] > hi) }
            assertWithMessage("n=$n lo=$lo hi=$hi").that(strideWindow(n, { times[it] }, lo, hi).toList()).isEqualTo(expected)
        }
    }

    private companion object {
        val GOLDEN = mapOf(
            "b_city_loop_5k" to "67484a4479549be2",
            "b_city_loop_5k/weak" to "9cac563c70482a35",
            "b_hill_baro" to "e365a7eb331232ce",
            "b_hill_baro/weak" to "055ffa5e8b42955d",
            "b_walk_mostly" to "dba63fc7c9fcf050",
            "b_walk_mostly/weak" to "8363a5ec8e698b20",
            "b_run_walk_intervals" to "1ab6140d6475c2cc",
            "b_run_walk_intervals/weak" to "8d87a2670a3c6d92",
            "b_tunnel_stepfill" to "4f6c97a5badd0a9b",
            "b_tunnel_stepfill/weak" to "da47f34356f08188",
            "b_paused" to "b7adcc76cf74bd7f",
            "b_paused/weak" to "019cfcd7b5525720",
            "b_stationary_table" to "cc32e51ea11c99f0",
            "b_stationary_table/weak" to "8b07978201177fba",
            "b_pb_10k" to "5d7d48a73365510d",
            "b_pb_10k/weak" to "7ac43eac1dd34bdf",
        )
    }
}
