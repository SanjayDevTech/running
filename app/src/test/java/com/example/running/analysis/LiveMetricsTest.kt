package com.example.running.analysis

import com.example.running.core.model.LiveStats
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.model.PressureSample
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.StepEvent
import com.example.running.testing.GpsFixture
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Replays `b_` fixtures through [DefaultLiveMetrics] the way the recording service would. */
class LiveMetricsTest {

    private class Replay(val metrics: DefaultLiveMetrics, val snapshots: List<Pair<Long, LiveStats>>, val end: LiveStats)

    private fun replay(name: String, withSteps: Boolean = true): Replay {
        val f = GpsFixture.load(name)
        val metrics = DefaultLiveMetricsFactory().create(f.runnerProfile(), f.startMillis) as DefaultLiveMetrics
        val pauses = f.toPauses()
        val events = mutableListOf<Pair<Long, () -> Unit>>()
        fun paused(t: Long) = pauses.any { t >= it.startMillis && t < it.endMillis!! }
        f.toLocationFixes().forEach { fix -> events += fix.timeMillis to { metrics.onFix(fix, paused(fix.timeMillis)) } }
        if (withSteps) {
            f.steps.forEach { s -> val t = f.startMillis + s.t; events += t to { metrics.onSteps(StepEvent(t), paused(t)) } }
            f.stepCounter.zipWithNext().forEach { (a, b) ->
                val t = f.startMillis + b.t
                val delta = (b.count - a.count).toInt()
                if (delta > 0) events += t to { metrics.onSteps(StepEvent(t, delta), paused(t)) }
            }
        }
        f.pressure.forEach { p -> val t = f.startMillis + p.t; events += t to { metrics.onPressure(PressureSample(t, p.hPa)) } }
        pauses.forEach { p ->
            events += p.startMillis to { metrics.onPauseChanged(true, p.startMillis) }
            events += p.endMillis!! to { metrics.onPauseChanged(false, p.endMillis!!) }
        }
        val snaps = mutableListOf<Pair<Long, LiveStats>>()
        var nextSnap = f.startMillis
        for ((t, action) in events.sortedBy { it.first }) {
            while (nextSnap <= t) { snaps += nextSnap to metrics.snapshot(nextSnap); nextSnap += 10_000 }
            action()
        }
        return Replay(metrics, snaps, metrics.snapshot(f.endMillis))
    }

    private fun truth(name: String) = GpsFixture.load(name).expect["truthDistanceM"]!!.jsonPrimitive.double

    @Test fun cityLoopLiveMatchesTruth() {
        val r = replay("b_city_loop_5k")
        val s = r.end
        assertThat(s.distanceM).isWithin(0.03 * 5_000).of(truth("b_city_loop_5k"))
        assertThat(s.elapsedMillis).isEqualTo(GpsFixture.load("b_city_loop_5k").let { it.endMillis - it.startMillis })
        assertThat(s.movingMillis.toDouble()).isWithin(25_000.0).of(1_651_000.0)
        assertThat(s.currentSplitIndex).isAnyOf(4, 5)
        assertThat(s.lastSplitPaceSecPerKm!!).isWithin(25.0).of(330.0)
        assertThat(s.avgPaceSecPerKm!!).isWithin(15.0).of(330.0)
        assertThat(s.steps!!).isGreaterThan(4_000)
        assertThat(s.cadenceSpm!!).isWithin(12.0).of(166.0)
        assertThat(s.caloriesKcal!!).isWithin(50.0).of(390.0)
        assertThat(s.elevationGainM).isNotNull()
        // Mid-run current pace is realistic; during the 40 s stop it is null.
        val mid = r.snapshots.first { it.first - GpsFixture.load("b_city_loop_5k").startMillis >= 600_000 }.second
        assertThat(mid.currentPaceSecPerKm!!).isWithin(40.0).of(330.0)
        assertThat(r.snapshots.any { it.second.currentPaceSecPerKm == null && it.second.elapsedMillis > 700_000 }).isTrue()
        // Monotonic distance and moving time.
        assertThat(r.snapshots.map { it.second.distanceM }).isInOrder()
        assertThat(r.snapshots.map { it.second.movingMillis }).isInOrder()
        val route = r.metrics.acceptedRoute()
        assertThat(route.points.size).isGreaterThan(1_000)
        assertThat(route.points.map { it.distanceM }).isInOrder()
    }

    @Test fun tunnelLiveUsesSteps() {
        val r = replay("b_tunnel_stepfill")
        assertThat(r.end.distanceM).isWithin(0.03 * 2_400).of(truth("b_tunnel_stepfill"))
        // Distance keeps growing inside the tunnel (no fixes there), from steps.
        val start = GpsFixture.load("b_tunnel_stepfill").startMillis
        val inTunnel = r.snapshots.filter { it.first - start in 350_000L..420_000L }.map { it.second.distanceM }
        assertThat(inTunnel.last() - inTunnel.first()).isGreaterThan(150.0)
        assertThat(r.metrics.acceptedRoute().points.count { it.stepFilled }).isAtLeast(10)
    }

    @Test fun tunnelWithoutStepsJumpsAtTheExit() {
        val r = replay("b_tunnel_stepfill", withSteps = false)
        assertThat(r.end.distanceM).isWithin(0.05 * 2_400).of(2_400.0)
        assertThat(r.end.steps).isNull()
        assertThat(r.end.cadenceSpm).isNull()
    }

    @Test fun stationaryPhoneStaysAtZero() {
        val r = replay("b_stationary_table")
        assertThat(r.end.distanceM).isLessThan(15.0)
        assertThat(r.end.movingMillis).isLessThan(30_000L)
        assertThat(r.end.currentPaceSecPerKm).isNull()
        assertThat(r.metrics.acceptedRoute().points.size).isLessThan(20)
    }

    @Test fun pausedMovementNotCounted() {
        val r = replay("b_paused")
        assertThat(r.end.distanceM).isWithin(0.03 * 1_800).of(truth("b_paused"))
        assertThat(r.end.movingMillis.toDouble()).isWithin(10_000.0).of(600_000.0)
        assertThat(r.end.elapsedMillis).isEqualTo(660_000L)
        val inPause = r.snapshots.filter { it.first - GpsFixture.load("b_paused").startMillis in 305_000L..355_000L }
        assertThat(inPause.map { it.second.distanceM }.distinct()).hasSize(1)
        assertThat(inPause.map { it.second.movingMillis }.distinct()).hasSize(1)
    }

    @Test fun barometerGainLive() {
        val r = replay("b_hill_baro")
        val range = GpsFixture.load("b_hill_baro").expect["elevationGainM"]!!.jsonObject
        assertThat(r.end.elevationGainM!!).isAtLeast(range["min"]!!.jsonPrimitive.double)
        assertThat(r.end.elevationGainM!!).isAtMost(range["max"]!!.jsonPrimitive.double)
    }

    @Test fun walkSegmentsColourTheLiveRoute() {
        val r = replay("b_run_walk_intervals")
        val kinds = r.metrics.acceptedRoute().points.map { it.segmentKind }
        assertThat(kinds.count { it == SegmentKind.WALK }).isGreaterThan(150)
        assertThat(kinds.count { it == SegmentKind.RUN }).isGreaterThan(800)
    }

    @Test fun pausedFlagOnFixIsHonoured() {
        val m = DefaultLiveMetrics(GpsFixture.load("b_paused").runnerProfile(), 0)
        fun fix(t: Long, lat: Double) = LocationFix(t, t * 1_000_000, lat, 77.0, null, null, 5f, null, 3f, 0.3f, 0f, LocationEngine.PLAY)
        for (i in 0..10) m.onFix(fix(i * 1000L, 12.0 + i * 0.000027), paused = false)
        val before = m.snapshot(10_000).distanceM
        for (i in 11..20) m.onFix(fix(i * 1000L, 12.0 + i * 0.000027), paused = true)
        assertThat(m.snapshot(20_000).distanceM).isEqualTo(before)
        assertThat(before).isWithin(3.0).of(30.0)
        assertThat(m.snapshot(20_000).elapsedMillis).isEqualTo(20_000L)
    }

    @Test fun gapWithoutEventsCountsAsMovingSoPaceStaysSane() {
        // 3 m/s north, then 25 s with no events at all (process restart), then running on.
        val m = DefaultLiveMetrics(GpsFixture.load("b_paused").runnerProfile(), 0)
        fun fix(t: Long) = LocationFix(t, t * 1_000_000, 12.0 + t / 1000.0 * 0.000027, 77.0, null, null, 5f, null, 3f, 0.3f, 0f, LocationEngine.PLAY)
        for (i in 0..30) m.onFix(fix(i * 1000L), paused = false)
        for (i in 55..60) m.onFix(fix(i * 1000L), paused = false)
        val s = m.snapshot(60_000)
        assertThat(s.distanceM).isWithin(10.0).of(180.0)
        assertThat(s.movingMillis.toDouble()).isWithin(6_000.0).of(60_000.0)
        assertThat(s.avgPaceSecPerKm!!).isWithin(40.0).of(333.0)
        assertThat(s.currentPaceSecPerKm!!).isWithin(60.0).of(333.0)
    }
}
