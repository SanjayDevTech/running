package com.example.running.analysis

import com.example.running.core.model.ActivityType
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.ElevationSource
import com.example.running.core.model.PointKind
import com.example.running.core.model.RunAnalysis
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.SensorType
import com.example.running.testing.GpsFixture
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test

/** Full [DefaultRunAnalyzer] on every `b_` fixture with tolerance assertions (architecture.md §6.4). */
class RunAnalyzerFixtureTest {
    private val analyzer = DefaultRunAnalyzer(DefaultRunNamer())

    private fun run(name: String): Pair<GpsFixture, RunAnalysis> {
        val f = GpsFixture.load(name)
        return f to analyzer.analyze(f.toAnalysisInput())
    }

    private fun JsonObject.range(key: String) = this[key]!!.jsonObject.let { it["min"]!!.jsonPrimitive.double..it["max"]!!.jsonPrimitive.double }

    private fun assertIn(label: String, value: Double, range: ClosedFloatingPointRange<Double>) =
        assertWithMessage("$label = $value, expected $range").that(value in range).isTrue()

    private fun assertWellFormed(a: RunAnalysis) {
        assertThat(a.filteredPoints.all { it.kind == PointKind.FILTERED && it.distanceM != null }).isTrue()
        val pts = a.filteredPoints.filter { !it.paused }
        assertThat(pts.all { it.segmentKind != null }).isTrue()
        assertThat(pts.map { it.timeMillis }).isInOrder()
        assertThat(pts.map { it.distanceM!! }).isInOrder()
        assertThat(pts.last().distanceM!!).isWithin(1e-6).of(a.stats.distanceM)
        if (a.splits.isNotEmpty()) {
            assertThat(a.splits.sumOf { it.distanceM }).isWithin(10.0).of(a.stats.distanceM)
            assertThat(a.splits.sumOf { it.movingMillis }.toDouble()).isWithin(a.splits.size * 2.0 + 1_000.0).of(a.stats.movingMillis.toDouble())
            assertThat(a.splits.map { it.index }).isEqualTo(a.splits.indices.toList())
        }
        assertThat(a.segments.map { it.startMillis }).isInOrder()
        assertThat(a.stats.movingMillis).isAtMost(a.stats.elapsedMillis - a.stats.pausedMillis)
        assertThat(a.analysisVersion).isEqualTo(AnalysisTuning.ANALYSIS_VERSION)
    }

    @Test fun stationaryPhoneHasNoDistanceOrMovingTime() {
        val (f, a) = run("b_stationary_table")
        assertIn("distance", a.stats.distanceM, f.expect.range("distanceM"))
        assertIn("moving", a.stats.movingMillis.toDouble(), f.expect.range("movingMillis"))
        assertThat(a.splits).isEmpty()
        assertThat(a.bestEfforts).isEmpty()
        assertThat(a.segments.map { it.kind }.toSet()).containsExactly(SegmentKind.STOP)
        assertThat(a.stats.steps).isNull()
        assertThat(a.stats.elevationGainM ?: 0.0).isLessThan(3.0)
    }

    @Test fun stationaryWithoutDopplerStillGated() {
        val f = GpsFixture.load("b_stationary_table")
        val input = f.toAnalysisInput().let { i -> i.copy(rawPoints = i.rawPoints.map { it.copy(speedMps = null, bearingDeg = null) }) }
        val a = analyzer.analyze(input)
        assertThat(a.stats.distanceM).isLessThan(25.0)
    }

    @Test fun cityLoopWithUrbanCanyonAndStop() {
        val (f, a) = run("b_city_loop_5k")
        assertWellFormed(a)
        assertIn("distance", a.stats.distanceM, f.expect.range("distanceM"))
        assertIn("moving", a.stats.movingMillis.toDouble(), f.expect.range("movingMillis"))
        val stops = a.segments.filter { it.kind == SegmentKind.STOP }
        assertThat(stops).hasSize(1)
        assertThat((stops[0].endMillis - stops[0].startMillis).toDouble()).isWithin(8_000.0).of(40_000.0)
        assertThat(stops[0].startDistanceM).isWithin(60.0).of(2_400.0)
        assertThat(a.suggestedType).isEqualTo(ActivityType.RUN)
        assertThat(a.suggestedName).isEqualTo("Morning Run")
        assertThat(a.splits.size).isIn(5..6)
        a.splits.take(5).forEach { assertThat(it.paceSecPerKm!!).isWithin(12.0).of(330.0) }
        // The stop sits in split 3: its elapsed time is longer than its moving time.
        assertThat(a.splits[2].elapsedMillis - a.splits[2].movingMillis).isAtLeast(30_000L)
        assertThat(a.stats.elevationSource).isEqualTo(ElevationSource.BAROMETER)
        assertThat(a.stats.stepFilledM).isGreaterThan(0.0)
        assertThat(a.stats.avgCadenceSpm!!).isWithin(5.0).of(166.0)
        assertThat(a.stats.caloriesKcal!!).isWithin(40.0).of(390.0)
        assertThat(a.stats.maxSpeedMps!!).isLessThan(4.5)
        // Best efforts only for distances actually covered (the measured total may land just under 5 km).
        assertThat(a.bestEfforts.map { it.distance }).contains(BestEffortDistance.K1)
        assertThat(a.bestEfforts.all { it.distance.meters <= a.stats.distanceM }).isTrue()
    }

    @Test fun urbanCanyonSpikesDoNotInflateDistance() {
        val (_, a) = run("b_city_loop_5k")
        // Between km 2 and 3 the pace must stay realistic: no split faster than 5:00/km.
        assertThat(a.splits[2].paceSecPerKm!!).isGreaterThan(300.0)
        val maxHop = a.filteredPoints.zipWithNext { p, q -> q.distanceM!! - p.distanceM!! }.max()
        assertThat(maxHop).isLessThan(30.0)
    }

    @Test fun tunnelIsStepFilledAndDashed() {
        val (f, a) = run("b_tunnel_stepfill")
        assertWellFormed(a)
        assertIn("distance", a.stats.distanceM, f.expect.range("distanceM"))
        assertIn("stepFilled", a.stats.stepFilledM, f.expect.range("stepFilledM"))
        val filled = a.filteredPoints.filter { it.stepFilled }
        assertThat(filled.size).isAtLeast(15)
        // Inserted points inside the gap carry no engine and are spread along the tunnel.
        assertThat(filled.count { it.engine == null }).isAtLeast(15)
        assertThat(filled.any { it.distanceM!! in 1_100.0..1_200.0 }).isTrue()
        // No stop is invented in the tunnel: moving time ~ elapsed.
        assertThat(a.segments.none { it.kind == SegmentKind.STOP }).isTrue()
        assertThat(a.learnedRunStrideM!!).isWithin(0.08).of(3.0 / (170.0 / 60.0))
    }

    @Test fun tunnelWithoutStepsFallsBackToStraightLine() {
        val f = GpsFixture.load("b_tunnel_stepfill")
        val a = analyzer.analyze(f.toAnalysisInput().copy(sensorSamples = emptyList()))
        assertThat(a.stats.stepFilledM).isEqualTo(0.0)
        assertThat(a.filteredPoints.none { it.stepFilled }).isTrue()
        // The tunnel is nearly straight, so the straight line is close; weak stretch uses GPS.
        assertThat(a.stats.distanceM).isWithin(0.04 * 2_400).of(2_400.0)
        assertThat(a.stats.steps).isNull()
        assertThat(a.learnedRunStrideM).isNull()
    }

    @Test fun runWalkIntervalsAreLabelled() {
        val (f, a) = run("b_run_walk_intervals")
        assertWellFormed(a)
        val walks = a.segments.filter { it.kind == SegmentKind.WALK }
        val runs = a.segments.filter { it.kind == SegmentKind.RUN }
        assertThat(walks).hasSize(f.expect["walkSegments"]!!.jsonPrimitive.int)
        assertThat(runs).hasSize(f.expect["runSegments"]!!.jsonPrimitive.int)
        walks.forEach { assertThat((it.endMillis - it.startMillis).toDouble()).isWithin(8_000.0).of(60_000.0) }
        walks.forEach { assertThat(it.avgCadenceSpm!!).isWithin(10.0).of(112.0) }
        assertIn("walkFraction", a.stats.walkFraction!!, f.expect.range("walkFraction"))
        assertThat(a.suggestedType).isEqualTo(ActivityType.RUN)
        assertThat(a.filteredPoints.count { it.segmentKind == SegmentKind.WALK }).isGreaterThan(200)
        // Flat route on GPS altitude only: smoothing + dead band keep phantom climb small.
        assertThat(a.stats.elevationSource).isEqualTo(ElevationSource.GPS)
        assertThat(a.stats.elevationGainM!!).isLessThan(25.0)
        assertThat(a.learnedWalkStrideM!!).isWithin(0.1).of(1.4 / (112.0 / 60.0))
    }

    @Test fun mostlyWalkingIsAWalk() {
        val (f, a) = run("b_walk_mostly")
        assertWellFormed(a)
        assertThat(a.suggestedType).isEqualTo(ActivityType.WALK)
        assertThat(a.suggestedName).isEqualTo("Morning Walk")
        assertIn("distance", a.stats.distanceM, f.expect.range("distanceM"))
        assertThat(a.segments.count { it.kind == SegmentKind.RUN }).isEqualTo(1)
    }

    @Test fun manualTypeWins() {
        val f = GpsFixture.load("b_walk_mostly")
        val a = analyzer.analyze(f.toAnalysisInput().copy(manualType = ActivityType.RUN))
        assertThat(a.suggestedType).isEqualTo(ActivityType.RUN)
        assertThat(a.suggestedName).isEqualTo("Morning Run")
    }

    @Test fun barometerElevation() {
        val (f, a) = run("b_hill_baro")
        assertWellFormed(a)
        assertThat(a.stats.elevationSource).isEqualTo(ElevationSource.BAROMETER)
        assertIn("gain", a.stats.elevationGainM!!, f.expect.range("elevationGainM"))
        assertIn("loss", a.stats.elevationLossM!!, f.expect.range("elevationGainM"))
        assertThat(a.splits[0].elevationGainM!!).isWithin(5.0).of(40.0)
        assertThat(a.splits[1].elevationGainM!!).isLessThan(3.0)
        assertThat(a.splits[2].elevationLossM!!).isWithin(5.0).of(40.0)
        val elev = a.filteredPoints.mapNotNull { it.elevationM }
        assertThat(elev.max() - elev.min()).isWithin(5.0).of(40.0)
    }

    @Test fun gpsElevationFallback() {
        val f = GpsFixture.load("b_hill_baro")
        val input = f.toAnalysisInput().let { i -> i.copy(sensorSamples = i.sensorSamples.filter { it.type != SensorType.PRESSURE }) }
        val a = analyzer.analyze(input)
        assertThat(a.stats.elevationSource).isEqualTo(ElevationSource.GPS)
        assertIn("gpsGain", a.stats.elevationGainM!!, f.expect.range("gpsElevationGainM"))
    }

    @Test fun bestEffortsOnMovingTime() {
        val (f, a) = run("b_pb_10k")
        assertWellFormed(a)
        val byDistance = a.bestEfforts.associateBy { it.distance }
        assertThat(byDistance.keys).containsExactly(BestEffortDistance.K1, BestEffortDistance.K5, BestEffortDistance.K10)
        assertIn("1K", byDistance[BestEffortDistance.K1]!!.durationMillis.toDouble(), f.expect.range("k1Millis"))
        assertIn("5K", byDistance[BestEffortDistance.K5]!!.durationMillis.toDouble(), f.expect.range("k5Millis"))
        assertIn("10K", byDistance[BestEffortDistance.K10]!!.durationMillis.toDouble(), f.expect.range("k10Millis"))
        assertThat(byDistance[BestEffortDistance.K1]!!.startDistanceM).isWithin(60.0).of(6_000.0)
        assertThat(a.splits).hasSize(f.expect["splits"]!!.jsonPrimitive.int)
        assertThat(a.splits[6].paceSecPerKm!!).isWithin(10.0).of(240.0)
        assertThat(a.stats.steps!!).isGreaterThan(9_000)
        assertThat(a.segments.count { it.kind == SegmentKind.STOP }).isEqualTo(1)
    }

    @Test fun pausedMovementIsExcluded() {
        val (f, a) = run("b_paused")
        assertWellFormed(a)
        assertIn("distance", a.stats.distanceM, f.expect.range("distanceM"))
        assertThat(a.stats.pausedMillis).isEqualTo(f.expect["pausedMillis"]!!.jsonPrimitive.long)
        assertThat(a.stats.movingMillis.toDouble()).isWithin(6_000.0).of(600_000.0)
        // Fixes during the pause are kept, flagged paused, and add no distance.
        val inPause = a.filteredPoints.filter { it.timeMillis - f.startMillis in 300_001L..359_999L }
        assertThat(inPause.size).isAtLeast(55)
        assertThat(inPause.all { it.paused && it.segmentKind == null && it.paceSecPerKm == null }).isTrue()
        assertThat(inPause.map { it.distanceM }.distinct()).hasSize(1)
        assertThat(a.filteredPoints.filter { !it.paused }.none { it.timeMillis - f.startMillis in 300_001L..359_999L }).isTrue()
        assertThat(a.segments.none { it.startMillis < f.startMillis + 330_000 && it.endMillis > f.startMillis + 330_000 }).isTrue()
    }

    @Test fun cleanStraightLineFromFoundationFixture() {
        val (f, a) = run("f0_straight_1k")
        assertIn("distance", a.stats.distanceM, f.expect.range("distanceM"))
        assertThat(a.splits).hasSize(1)
    }

    @Test fun emptyInputGivesEmptyAnalysis() {
        val f = GpsFixture.load("f0_straight_1k")
        val a = analyzer.analyze(f.toAnalysisInput().copy(rawPoints = emptyList(), sensorSamples = emptyList()))
        assertThat(a.stats.distanceM).isEqualTo(0.0)
        assertThat(a.filteredPoints).isEmpty()
        assertThat(a.splits).isEmpty()
        assertThat(a.segments).isEmpty()
        assertThat(a.stats.elapsedMillis).isEqualTo(334_000L)
        assertThat(a.suggestedType).isEqualTo(ActivityType.RUN)
    }

    @Test fun printSummaries() {
        // Diagnostic output (shown with --info); never fails.
        for (spec in AnalysisFixtures.specs) {
            val (f, a) = run(spec.name)
            println("${spec.name}: dist=${"%.1f".format(a.stats.distanceM)} moving=${a.stats.movingMillis} " +
                "filled=${"%.1f".format(a.stats.stepFilledM)} gain=${a.stats.elevationGainM} type=${a.suggestedType} " +
                "segs=${a.segments.map { "${it.kind}:${(it.endMillis - it.startMillis) / 1000}" }} " +
                "efforts=${a.bestEfforts.map { "${it.distance}:${it.durationMillis}" }} expect=${f.expect}")
        }
    }
}
