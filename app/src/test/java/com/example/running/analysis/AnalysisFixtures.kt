package com.example.running.analysis

import com.example.running.testing.GpsFixture
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Deterministic generator for track B's `b_*.json` fixtures (architecture.md §6.3). The committed
 * files in `src/test/resources/fixtures/` are exactly this output; [AnalysisFixturesTest] fails if
 * they drift, and writes fresh copies to `app/build/generated-fixtures/` to copy over.
 *
 * Ground truth is simulated at 1 Hz: a runner follows a gently curving path through a script of
 * [Leg]s; fixes add Gaussian position noise, Doppler noise and per-second [Degrade] effects
 * (dropouts, accuracy spikes); steps come from the cadence; pressure from the altitude.
 */
internal object AnalysisFixtures {

    sealed interface Leg {
        /** Moves for [seconds] (or until [untilDistanceM]) at [speed] m/s and [cadence] spm on [grade]. */
        data class Move(val speed: Double, val cadence: Double, val seconds: Int? = null, val untilDistanceM: Double? = null, val grade: Double = 0.0) : Leg
        data class Stop(val seconds: Int) : Leg
    }

    /** Per-fix degradation decided from truth distance/time. */
    data class Degrade(val drop: Boolean = false, val accuracyM: Double? = null, val errorM: Double? = null)

    data class Spec(
        val name: String,
        val description: String,
        val legs: List<Leg>,
        val seed: Long,
        val noiseM: Double = 3.0,
        val steps: StepMode = StepMode.DETECTOR,
        val barometer: Boolean = false,
        /** Paused windows in seconds from the start; the runner keeps moving at walking pace. */
        val pausesSec: List<IntRange> = emptyList(),
        val degrade: (tSec: Int, distanceM: Double) -> Degrade = { _, _ -> Degrade() },
        val expect: (Truth) -> JsonObject,
    )

    enum class StepMode { NONE, DETECTOR, COUNTER }

    /** Ground-truth summary handed to `expect`. */
    data class Truth(
        val distanceM: Double, val movingSec: Int, val elapsedSec: Int, val stops: Int,
        val walkDistanceM: Double, val gainM: Double,
    )

    const val START_MILLIS = 1_764_040_800_000L // 2025-11-25 08:50 Asia/Kolkata (a morning run)

    private const val PAUSE_WALK_SPEED = 1.4

    fun generate(spec: Spec): GpsFixture {
        val rnd = Random(spec.seed)
        var lat = 12.9716
        var lon = 77.5946
        var heading = rnd.nextDouble(0.0, 2 * PI)
        var alt = 900.0
        var dist = 0.0
        var walk = 0.0
        var gain = 0.0
        var movingSec = 0
        var stops = 0
        var stepPhase = 0.0
        var stepCount = 0L
        var altErr = 0.0
        val fixes = mutableListOf<GpsFixture.FixtureFix>()
        val steps = mutableListOf<GpsFixture.FixtureStep>()
        val counter = mutableListOf<GpsFixture.FixtureCount>()
        val pressure = mutableListOf<GpsFixture.FixturePressure>()

        // Expand legs into a per-second script.
        data class Sec(val speed: Double, val cadence: Double, val grade: Double, val stopped: Boolean)
        var t = 0
        fun emit(sec: Sec, paused: Boolean) {
            val speed = if (paused) PAUSE_WALK_SPEED else sec.speed
            val cadence = if (paused) 110.0 else sec.cadence
            if (speed > 0) {
                heading += rnd.nextDouble(-0.06, 0.06)
                lat += speed * cos(heading) / 111_320.0
                lon += speed * sin(heading) / (111_320.0 * cos(Math.toRadians(lat)))
                val dAlt = if (paused) 0.0 else speed * sec.grade
                alt += dAlt
                if (!paused) {
                    dist += speed
                    if (dAlt > 0) gain += dAlt
                    if (cadence < 135) walk += speed
                }
            }
            if (!paused && !sec.stopped) movingSec++
            t++
            // Steps during the second that just elapsed.
            if (spec.steps != StepMode.NONE && cadence > 0) {
                stepPhase += cadence / 60.0
                while (stepPhase >= 1.0) {
                    stepPhase -= 1.0
                    stepCount++
                    val at = ((t - stepPhase * 60.0 / cadence) * 1000).toLong()
                    if (spec.steps == StepMode.DETECTOR) steps += GpsFixture.FixtureStep(at)
                }
                if (spec.steps == StepMode.COUNTER && t % 5 == 0) counter += GpsFixture.FixtureCount(t * 1000L, 10_000 + stepCount)
            } else if (spec.steps == StepMode.COUNTER && t % 5 == 0) {
                counter += GpsFixture.FixtureCount(t * 1000L, 10_000 + stepCount)
            }
            if (spec.barometer) {
                val hPa = 1_013.25 * (1 - alt / 44_330.77).pow(1 / 0.190263) + gaussian(rnd) * 0.03
                pressure += GpsFixture.FixturePressure(t * 1000L, r(hPa, 3))
            }
            val deg = spec.degrade(t, dist)
            if (deg.drop) return
            val acc = deg.accuracyM ?: (spec.noiseM * 1.5 + 1.0)
            val err = deg.errorM ?: spec.noiseM
            altErr = altErr * 0.95 + gaussian(rnd) * 0.35
            val reportedSpeed = if (speed <= 0) abs(gaussian(rnd)) * 0.15 else (speed + gaussian(rnd) * 0.15).coerceAtLeast(0.0)
            fixes += GpsFixture.FixtureFix(
                t = t * 1000L,
                lat = r(lat + gaussian(rnd) * err / 111_320.0, 7),
                lon = r(lon + gaussian(rnd) * err / (111_320.0 * cos(Math.toRadians(lat))), 7),
                alt = r(alt + 65.0 + altErr * 3, 1), msl = r(alt + altErr * 3, 1),
                hAcc = r(acc, 1).toFloat(), vAcc = 5f,
                spd = r(reportedSpeed, 2).toFloat(), sAcc = if (acc > 20) 1.2f else 0.3f,
                brg = if (speed > 0.5) r(((Math.toDegrees(heading) % 360 + 360) % 360) + gaussian(rnd) * 3, 1).toFloat() else null,
            )
        }

        // Fix at t = 0 (standing at the start line).
        fixes += GpsFixture.FixtureFix(0, r(lat, 7), r(lon, 7), r(alt + 65, 1), r(alt, 1), (spec.noiseM * 1.5 + 1).toFloat(), 5f, 0f, 0.3f, null)
        if (spec.barometer) pressure += GpsFixture.FixturePressure(0, r(1_013.25 * (1 - alt / 44_330.77).pow(1 / 0.190263), 3))
        if (spec.steps == StepMode.COUNTER) counter += GpsFixture.FixtureCount(0, 10_000)

        fun paused(sec: Int) = spec.pausesSec.any { sec in it }
        for (leg in spec.legs) when (leg) {
            is Leg.Stop -> {
                stops++
                repeat(leg.seconds) { emit(Sec(0.0, 0.0, 0.0, true), paused(t)) }
            }
            is Leg.Move -> {
                val endDist = leg.untilDistanceM
                var n = 0
                while (if (endDist != null) dist < endDist else n < leg.seconds!!) {
                    emit(Sec(leg.speed, leg.cadence, leg.grade, false), paused(t))
                    n++
                }
            }
        }
        val truth = Truth(dist, movingSec, t, stops, walk, gain)
        return GpsFixture(
            name = spec.name, description = spec.description, zoneId = "Asia/Kolkata", startMillis = START_MILLIS,
            profile = GpsFixture.FixtureProfile(weightKg = 70.0, ageYears = 30, sex = "MALE", heightCm = 175),
            fixes = fixes, steps = steps, stepCounter = counter, pressure = pressure,
            pauses = spec.pausesSec.map { GpsFixture.FixturePause(it.first * 1000L, (it.last + 1) * 1000L) },
            expect = spec.expect(truth),
        )
    }

    private fun r(v: Double, decimals: Int): Double = BigDecimal(v).setScale(decimals, RoundingMode.HALF_UP).toDouble()

    private fun gaussian(rnd: Random): Double {
        val u1 = rnd.nextDouble().coerceAtLeast(1e-12)
        val u2 = rnd.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2 * PI * u2)
    }

    private fun range(min: Double, max: Double, decimals: Int = 1) = buildJsonObject { put("min", r(min, decimals)); put("max", r(max, decimals)) }

    private fun truthJson(truth: Truth, extra: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("truthDistanceM", r(truth.distanceM, 1))
        put("truthMovingMillis", truth.movingSec * 1000L)
        put("truthWalkDistanceM", r(truth.walkDistanceM, 1))
        put("truthGainM", r(truth.gainM, 1))
        extra()
    }

    // ---- the fixtures ----------------------------------------------------------------------

    private const val PACE_530 = 1000.0 / 330.0

    val specs: List<Spec> = listOf(
        Spec(
            name = "b_stationary_table",
            description = "Phone lying on a table for 10 min: 4 m position jitter, Doppler noise, no steps.",
            legs = listOf(Leg.Stop(600)), seed = 11, noiseM = 4.0, steps = StepMode.NONE,
            expect = { tr -> truthJson(tr) { put("distanceM", range(0.0, 15.0)); put("movingMillis", range(0.0, 30_000.0)) } },
        ),
        Spec(
            name = "b_city_loop_5k",
            description = "5 km at 5:30/km, urban canyon km 2–3 (20 % of fixes 50–80 m, 15 % 22–28 m), one 40 s traffic-light stop, barometer.",
            legs = listOf(Leg.Move(PACE_530, 166.0, untilDistanceM = 2_400.0), Leg.Stop(40), Leg.Move(PACE_530, 166.0, untilDistanceM = 5_000.0)),
            seed = 21, barometer = true,
            degrade = { tSec, d ->
                if (d in 2_000.0..3_000.0) {
                    val u = Random(tSec * 7919L).nextDouble()
                    when {
                        u < 0.20 -> Degrade(accuracyM = 50.0 + u * 150, errorM = 45.0)
                        u < 0.35 -> Degrade(accuracyM = 22.0 + (u - 0.2) * 40, errorM = 15.0)
                        else -> Degrade(accuracyM = 9.0, errorM = 5.0)
                    }
                } else Degrade()
            },
            expect = { tr -> truthJson(tr) {
                put("distanceM", range(tr.distanceM * 0.985, tr.distanceM * 1.015))
                put("movingMillis", range(tr.movingSec * 1000.0 - 12_000, tr.movingSec * 1000.0 + 8_000))
                put("type", "RUN"); put("stops", 1); put("splits", 5)
            } },
        ),
        Spec(
            name = "b_tunnel_stepfill",
            description = "2.4 km at 3.0 m/s with a 300 m tunnel (no fixes) and a 100 m stretch of 25 m accuracy; steps throughout.",
            legs = listOf(Leg.Move(3.0, 170.0, untilDistanceM = 2_400.0)), seed = 31,
            degrade = { _, d ->
                when (d) {
                    in 1_000.0..1_300.0 -> Degrade(drop = true)
                    in 1_800.0..1_900.0 -> Degrade(accuracyM = 25.0, errorM = 15.0)
                    else -> Degrade()
                }
            },
            expect = { tr -> truthJson(tr) {
                put("distanceM", range(tr.distanceM * 0.98, tr.distanceM * 1.02))
                put("stepFilledM", range(330.0, 480.0))
            } },
        ),
        Spec(
            name = "b_run_walk_intervals",
            description = "5 x (4 min run at 3.0 m/s, 166 spm / 1 min walk at 1.4 m/s, 112 spm).",
            legs = (1..5).flatMap { listOf(Leg.Move(3.0, 166.0, seconds = 240), Leg.Move(1.4, 112.0, seconds = 60)) },
            seed = 41,
            expect = { tr -> truthJson(tr) {
                put("type", "RUN"); put("walkSegments", 5); put("runSegments", 5)
                put("walkFraction", range(tr.walkDistanceM / tr.distanceM - 0.03, tr.walkDistanceM / tr.distanceM + 0.03, decimals = 3))
            } },
        ),
        Spec(
            name = "b_walk_mostly",
            description = "30 min walk at 1.45 m/s, 115 spm, with a 60 s jog in the middle.",
            legs = listOf(Leg.Move(1.45, 115.0, seconds = 870), Leg.Move(2.6, 155.0, seconds = 60), Leg.Move(1.45, 115.0, seconds = 870)),
            seed = 51,
            expect = { tr -> truthJson(tr) { put("type", "WALK"); put("distanceM", range(tr.distanceM * 0.98, tr.distanceM * 1.02)) } },
        ),
        Spec(
            name = "b_hill_baro",
            description = "3 km at 2.8 m/s: 1 km at +4 % (40 m), 1 km flat, 1 km at -4 %; barometer.",
            legs = listOf(
                Leg.Move(2.8, 165.0, untilDistanceM = 1_000.0, grade = 0.04),
                Leg.Move(2.8, 165.0, untilDistanceM = 2_000.0),
                Leg.Move(2.8, 165.0, untilDistanceM = 3_000.0, grade = -0.04),
            ),
            seed = 61, barometer = true,
            expect = { tr -> truthJson(tr) {
                put("elevationGainM", range(tr.gainM - 4, tr.gainM + 4))
                put("gpsElevationGainM", range(tr.gainM * 0.7, tr.gainM * 1.4))
            } },
        ),
        Spec(
            name = "b_pb_10k",
            description = "10.2 km at 5:30/km with km 6–7 at 4:00/km and a 30 s stop at 3 km; step counter (not detector).",
            legs = listOf(
                Leg.Move(PACE_530, 168.0, untilDistanceM = 3_000.0), Leg.Stop(30),
                Leg.Move(PACE_530, 168.0, untilDistanceM = 6_000.0),
                Leg.Move(1000.0 / 240.0, 182.0, untilDistanceM = 7_000.0),
                Leg.Move(PACE_530, 168.0, untilDistanceM = 10_200.0),
            ),
            seed = 71, steps = StepMode.COUNTER, noiseM = 2.5,
            expect = { _ -> buildJsonObject {
                put("k1Millis", range(234_000.0, 248_000.0))
                put("k5Millis", range(1_540_000.0, 1_585_000.0))
                put("k10Millis", range(3_180_000.0, 3_245_000.0))
                put("splits", 11)
            } },
        ),
        Spec(
            name = "b_paused",
            description = "10 min at 3.0 m/s with a 60 s pause at 5:00 during which the runner walks ~84 m (must not count).",
            legs = listOf(Leg.Move(3.0, 168.0, seconds = 300), Leg.Move(3.0, 168.0, seconds = 60), Leg.Move(3.0, 168.0, seconds = 300)),
            seed = 81, pausesSec = listOf(300..359),
            expect = { tr -> truthJson(tr) {
                put("distanceM", range(tr.distanceM * 0.98, tr.distanceM * 1.02))
                put("pausedMillis", 60_000)
            } },
        ),
    )

    fun spec(name: String): Spec = specs.first { it.name == name }
}
