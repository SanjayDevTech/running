package com.example.running.core

import com.example.running.core.demo.SyntheticRuns
import com.example.running.core.model.ActivityType
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.PointKind
import com.example.running.core.model.SegmentKind
import com.example.running.core.util.pathLengthMeters
import com.example.running.testing.GpsFixture
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.ZoneId

class SyntheticRunsAndFixtureTest {
    @Test fun fixtureLoads() {
        val f = GpsFixture.load("f0_straight_1k")
        assertThat(f.fixes).hasSize(335)
        val fixes = f.toLocationFixes()
        assertThat(fixes.first().timeMillis).isEqualTo(f.startMillis)
        val length = pathLengthMeters(fixes.map { GeoPoint(it.lat, it.lon) })
        val range = f.expect["distanceM"]!!.jsonObject
        assertThat(length).isAtLeast(range["min"]!!.jsonPrimitive.double)
        assertThat(length).isAtMost(range["max"]!!.jsonPrimitive.double)
        val input = f.toAnalysisInput("r1")
        assertThat(input.rawPoints.all { it.kind == PointKind.RAW && it.isPrimary && !it.paused }).isTrue()
        assertThat(input.endedAtMillis - input.startedAtMillis).isEqualTo(334_000L)
        assertThat(f.runnerProfile().heightCm).isEqualTo(175)
    }

    @Test fun syntheticRunIsDeterministicAndConsistent() {
        val spec = SyntheticRuns.Spec(distanceM = 5_200.0, paceSecPerKm = 300.0, stopSeconds = 40, seed = 7)
        val a = SyntheticRuns.runGraph("x", 1_000_000L, "Asia/Kolkata", spec)
        val b = SyntheticRuns.runGraph("x", 1_000_000L, "Asia/Kolkata", spec)
        assertThat(a).isEqualTo(b)
        val s = a.run.stats
        assertThat(s.distanceM).isAtLeast(5_200.0)
        assertThat(s.elapsedMillis - s.movingMillis).isEqualTo(40_000L)
        assertThat(a.splits).hasSize(6)
        assertThat(a.splits.dropLast(1).all { it.distanceM in 990.0..1_010.0 }).isTrue()
        assertThat(a.segments.map { it.kind }).contains(SegmentKind.STOP)
        assertThat(a.bestEfforts.map { it.distance }).containsExactly(BestEffortDistance.K1, BestEffortDistance.K5)
        val k1 = a.bestEfforts.first { it.distance == BestEffortDistance.K1 }
        assertThat(k1.durationMillis).isAtMost(320_000L)
        assertThat(a.points.count { it.kind == PointKind.RAW }).isEqualTo(a.points.count { it.kind == PointKind.FILTERED })
        assertThat(a.run.type).isEqualTo(ActivityType.RUN)
    }

    @Test fun demoGraphsHaveStableIds() {
        val zone = ZoneId.of("Asia/Kolkata")
        val g = SyntheticRuns.demoGraphs(1_764_040_800_000L, zone, count = 6, weeks = 2)
        assertThat(g.map { it.run.id }).containsExactly("demo-00", "demo-01", "demo-02", "demo-03", "demo-04", "demo-05").inOrder()
        assertThat(g.map { it.run.startedAtMillis }).isInOrder()
        assertThat(g.all { it.run.startedAtMillis < 1_764_040_800_000L }).isTrue()
    }
}
