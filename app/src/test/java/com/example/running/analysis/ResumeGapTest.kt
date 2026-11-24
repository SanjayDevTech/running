package com.example.running.analysis

import androidx.compose.ui.graphics.toArgb
import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.model.MapRoute
import com.example.running.core.model.PointKind
import com.example.running.core.model.RunAnalysis
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.SensorSample
import com.example.running.core.model.SensorType
import com.example.running.core.model.StepEvent
import com.example.running.core.model.TrackPoint
import com.example.running.core.util.METERS_PER_DEGREE_LAT
import com.example.running.core.util.metersPerDegreeLon
import com.example.running.map.RouteSegments
import com.example.running.ui.theme.StepFilled
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.hypot

/**
 * A process-death gap with a corner in it: 3 m/s at 180 spm north for 120 s, the app dies, the runner
 * keeps going north 30 s then turns east for 30 s, recording resumes at 180 s and goes on east for 240 s.
 * The straight line across the gap (~127 m) is shorter than the path (180 m).
 */
class ResumeGapTest {

    private val t0 = 1_764_040_800_000L
    private val speed = 3.0
    private val cadence = 180.0
    private val lat0 = 12.9716
    private val lon0 = 77.5946
    private val deathSec = 120
    private val resumeSec = 180
    private val endSec = 420
    private val gapSteps = cadence * (resumeSec - deathSec) / 60.0 // 180 steps
    private val chordM = hypot(30 * speed, 30 * speed)
    private val pathM = (resumeSec - deathSec) * speed

    /** Position (east, north) in metres at second [s] of the true path. */
    private fun pos(s: Double): Pair<Double, Double> = if (s <= 150) 0.0 to s * speed else (s - 150) * speed to 150 * speed

    private fun fix(s: Int): LocationFix {
        val (e, n) = pos(s.toDouble())
        val north = s <= 150
        return LocationFix(
            timeMillis = t0 + s * 1_000L, elapsedRealtimeNanos = s * 1_000_000_000L,
            lat = lat0 + n / METERS_PER_DEGREE_LAT, lon = lon0 + e / metersPerDegreeLon(lat0),
            altitudeM = null, mslAltitudeM = null, horizontalAccuracyM = 4f, verticalAccuracyM = null,
            speedMps = speed.toFloat(), speedAccuracyMps = 0.3f, bearingDeg = if (north) 0f else 90f,
            engine = LocationEngine.PLAY,
        )
    }

    private val fixSeconds = (0 until deathSec) + (resumeSec until endSec)
    private val stepPeriodMillis = (60_000 / cadence).toLong()

    /** Detector step times the app saw (none while it was dead). */
    private val stepTimes: List<Long> = generateSequence(t0 + stepPeriodMillis) { it + stepPeriodMillis }
        .takeWhile { it <= t0 + endSec * 1_000L }
        .filter { it <= t0 + deathSec * 1_000L || it > t0 + resumeSec * 1_000L }
        .toList()

    private fun input(gapValue: Double): AnalysisInput = AnalysisInput(
        runId = "r", startedAtMillis = t0, endedAtMillis = t0 + endSec * 1_000L, zoneId = "UTC",
        rawPoints = fixSeconds.map { fix(it).toRawPoint("r", isPrimary = true, paused = false) },
        pauses = emptyList(),
        sensorSamples = (stepTimes.map { SensorSample(SensorType.STEP_DETECTOR, it, 1.0) } +
            SensorSample(SensorType.RESUME_GAP, t0 + resumeSec * 1_000L, gapValue)).sortedBy { it.timeMillis },
        profile = RunnerProfile(heightCm = 175),
        manualType = null,
    )

    private fun analyze(gapValue: Double): RunAnalysis = DefaultRunAnalyzer(DefaultRunNamer()).analyze(input(gapValue))

    private val goodDistance = (deathSec - 1 + endSec - 1 - resumeSec) * speed

    private fun gapPoints(a: RunAnalysis): List<TrackPoint> =
        a.filteredPoints.filter { it.timeMillis > t0 + (deathSec - 1) * 1_000L && it.timeMillis < t0 + resumeSec * 1_000L }

    private fun assertGapDrawnAndCharted(a: RunAnalysis) {
        val inGap = gapPoints(a)
        assertThat(inGap).isNotEmpty()
        assertThat(inGap.all { it.stepFilled && it.paceSecPerKm == null && it.kind == PointKind.FILTERED }).isTrue()
        // The closing fix is dashed too; points away from the gap keep their pace.
        assertThat(a.filteredPoints.first { it.timeMillis == t0 + resumeSec * 1_000L }.stepFilled).isTrue()
        assertThat(a.filteredPoints.first { it.timeMillis == t0 + 60_000 }.paceSecPerKm!!).isWithin(20.0).of(1_000 / speed)
        // Points right next to the gap get the pace of their own side, not one mixed with the gap.
        val edges = a.filteredPoints.filter {
            it.timeMillis in (t0 + (deathSec - 3) * 1_000L)..(t0 + (deathSec - 1) * 1_000L) ||
                it.timeMillis in (t0 + (resumeSec + 1) * 1_000L)..(t0 + (resumeSec + 3) * 1_000L)
        }
        assertThat(edges).isNotEmpty()
        edges.forEach { assertThat(it.paceSecPerKm!!).isWithin(20.0).of(1_000 / speed) }

        // Map: the gap is one grey dashed line with no pace colour.
        val dashed = RouteSegments.build(MapRoute.fromTrackPoints(a.filteredPoints)).filter { it.stepFilled }
        assertThat(dashed).hasSize(1)
        assertThat(dashed.single().argb).isEqualTo(StepFilled.toArgb())

        // Pace chart: the line breaks over the gap instead of plotting a pace there.
        val series = DefaultRunSeriesBuilder().build(a.filteredPoints)
        val gapFrom = (deathSec - 1) * speed / 1_000
        val gapTo = gapFrom + a.stats.distanceM / 1_000 - goodDistance / 1_000
        assertThat(series.paceBreaksKm).hasSize(1)
        assertThat(series.paceBreaksKm.single()).isIn(com.google.common.collect.Range.open(gapFrom, gapTo))
        assertThat(series.pace.none { it.distanceKm > gapFrom + 0.01 && it.distanceKm < gapTo - 0.01 }).isTrue()

        // Moving time keeps the gap (the runner was moving).
        assertThat(a.stats.movingMillis.toDouble()).isWithin(3_000.0).of((endSec - 1) * 1_000.0)
    }

    @Test fun gapWithCornerUsesStepDistance() {
        val a = analyze(gapSteps)
        val stride = a.stats.strideMUsed!!
        assertThat(stride).isWithin(0.05).of(speed * 60 / cadence)
        assertThat(a.stats.distanceM).isWithin(15.0).of(goodDistance + speed + pathM)
        assertThat(a.stats.distanceM).isGreaterThan(goodDistance + chordM + 30)
        assertThat(a.stats.steps!!.toDouble()).isWithin(5.0).of(stepTimes.size + gapSteps)
        assertGapDrawnAndCharted(a)
        // Splits and best efforts use the step distance: the first km is not slowed by the gap.
        val k1 = a.splits.first()
        assertThat(k1.paceSecPerKm!!).isWithin(15.0).of(1_000 / speed)
        assertThat(a.bestEfforts.single { it.distance.label == "1K" }.durationMillis.toDouble()).isWithin(15_000.0).of(1_000_000 / speed)
    }

    @Test fun gapWithoutStepDataFallsBackToStraightLine() {
        val a = analyze(-1.0)
        assertThat(a.stats.distanceM).isWithin(15.0).of(goodDistance + speed + chordM)
        assertGapDrawnAndCharted(a)
    }

    @Test fun gapStepsRampIntoTheStepTimeline() {
        val samples = listOf(
            SensorSample(SensorType.STEP_DETECTOR, 1_000, 1.0),
            SensorSample(SensorType.STEP_DETECTOR, 2_000, 1.0),
            SensorSample(SensorType.RESUME_GAP, 62_000, 120.0),
            SensorSample(SensorType.RESUME_GAP, 70_000, -1.0), // unknown: adds nothing
        )
        val tl = StepTimeline.from(samples)
        assertThat(tl.stepsBetween(2_000, 62_000)).isWithin(1e-9).of(120.0)
        assertThat(tl.stepsBetween(2_000, 32_000)).isWithin(1e-9).of(60.0)
        assertThat(tl.at(80_000)).isWithin(1e-9).of(122.0)

        val counter = listOf(
            SensorSample(SensorType.STEP_COUNTER, 1_000, 0.0),
            SensorSample(SensorType.STEP_COUNTER, 2_000, 3.0),
            SensorSample(SensorType.RESUME_GAP, 62_000, 120.0),
            SensorSample(SensorType.STEP_COUNTER, 63_000, 5.0),
        )
        assertThat(StepTimeline.from(counter).at(63_000)).isWithin(1e-9).of(125.0)
    }

    // ---------------------------------------------------------------- live

    private fun live(gap: Double?): DefaultLiveMetrics {
        val m = DefaultLiveMetrics(RunnerProfile(heightCm = 175), t0)
        val events = mutableListOf<Triple<Long, Int, () -> Unit>>()
        fixSeconds.forEach { s -> val f = fix(s); events += Triple(f.timeMillis, 1, { m.onFix(f, false) }) }
        stepTimes.forEach { t -> events += Triple(t, 2, { m.onSteps(StepEvent(t), false) }) }
        events += Triple(t0 + resumeSec * 1_000L, 0, { m.onResumeGap(t0 + resumeSec * 1_000L, gap) })
        events.sortedWith(compareBy({ it.first }, { it.second })).forEach { it.third() }
        return m
    }

    @Test fun liveBridgesTheGapWithSteps() {
        val m = live(gapSteps)
        val s = m.snapshot(t0 + endSec * 1_000L)
        assertThat(s.distanceM).isWithin(25.0).of(goodDistance + speed + pathM)
        assertThat(s.movingMillis.toDouble()).isWithin(6_000.0).of(endSec * 1_000.0)
        assertThat(s.avgPaceSecPerKm!!).isWithin(20.0).of(1_000 / speed)
        assertThat(s.steps!!.toDouble()).isWithin(2.0).of(stepTimes.size + gapSteps)
        val dashed = RouteSegments.build(m.acceptedRoute()).filter { it.stepFilled }
        assertThat(dashed).hasSize(1)
        assertThat(dashed.single().argb).isEqualTo(StepFilled.toArgb())
        // The first point after the gap has no made-up pace.
        assertThat(m.acceptedRoute().points.first { it.stepFilled }.paceSecPerKm).isNull()
    }

    @Test fun liveFallsBackToStraightLineWithoutSteps() {
        val s = live(null).snapshot(t0 + endSec * 1_000L)
        assertThat(s.distanceM).isWithin(25.0).of(goodDistance + speed + chordM)
        assertThat(s.movingMillis.toDouble()).isWithin(6_000.0).of(endSec * 1_000.0)
    }
}
