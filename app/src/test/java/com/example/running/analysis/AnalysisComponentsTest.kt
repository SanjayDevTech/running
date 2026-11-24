package com.example.running.analysis

import com.example.running.core.model.ActivityType
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.ElevationSource
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.PauseInterval
import com.example.running.core.model.PointKind
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.SensorSample
import com.example.running.core.model.SensorType
import com.example.running.core.model.Sex
import com.example.running.core.model.TrackPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.random.Random

class AnalysisComponentsTest {

    // ---- Kalman ----------------------------------------------------------------------------

    @Test fun projectionRoundTrips() {
        val p = LocalProjection(GeoPoint(12.97, 77.59))
        val g = p.toGeo(p.east(77.6), p.north(12.98))
        assertThat(g.lat).isWithin(1e-9).of(12.98)
        assertThat(g.lon).isWithin(1e-9).of(77.6)
        assertThat(p.north(12.98)).isWithin(5.0).of(1_112.0)
    }

    @Test fun kalmanSmootherReducesNoiseAndTracksVelocity() {
        val rnd = Random(3)
        val truth = (0..300).map { it * 3.0 }   // 3 m/s north
        val ms = truth.mapIndexed { i, y ->
            KalmanMeasurement(i * 1000L, rnd.nextDouble(-6.0, 6.0), y + rnd.nextDouble(-6.0, 6.0), 8.0, 3.0 + rnd.nextDouble(-0.2, 0.2), 0.3, 0.0)
        }
        val out = GpsKalman.smooth(ms)
        val rawErr = ms.indices.sumOf { abs(ms[it].north - truth[it]) } / ms.size
        val smoothErr = ms.indices.sumOf { abs(out[it].north - truth[it]) } / ms.size
        assertThat(smoothErr).isLessThan(rawErr / 2)
        assertThat(out[150].vNorth).isWithin(0.3).of(3.0)
        assertThat(abs(out[150].vEast)).isLessThan(0.3)
    }

    @Test fun kalmanZeroVelocityWhenStill() {
        val kf = GpsKalman()
        var last: KalmanOutput? = null
        repeat(30) { last = kf.step(KalmanMeasurement(it * 1000L, 0.0, 0.0, 5.0, 0.1, 0.3, null)).second }
        assertThat(abs(last!!.vEast) + abs(last!!.vNorth)).isLessThan(0.1)
    }

    // ---- Pre-filter --------------------------------------------------------------------------

    @Test fun preFilterAppliesTherunnerThresholds() {
        val f = GpsPreFilter()
        assertThat(f.check(0, 12.0, 77.0, 31f, 3f)).isEqualTo(FixVerdict.POOR_ACCURACY)
        assertThat(f.check(0, 12.0, 77.0, 5f, 15f)).isEqualTo(FixVerdict.TOO_FAST)
        assertThat(f.check(0, 12.0, 77.0, 5f, 3f)).isEqualTo(FixVerdict.ACCEPTED)
        assertThat(f.check(1000, 12.000002, 77.0, 5f, 0.1f)).isEqualTo(FixVerdict.JITTER)            // 0.2 m
        assertThat(f.check(3000, 12.000002, 77.0, 5f, 0.1f)).isEqualTo(FixVerdict.ACCEPTED)          // jitter, but 3 s passed
        assertThat(f.check(4000, 12.001, 77.0, 5f, null)).isEqualTo(FixVerdict.TOO_FAST)             // 111 m in 1 s
        assertThat(f.check(3000, 12.000005, 77.0, 5f, null)).isEqualTo(FixVerdict.DUPLICATE_TIME)
        assertThat(f.check(5000, 12.00003, 77.0, 5f, 3f)).isEqualTo(FixVerdict.ACCEPTED)
    }

    @Test fun preFilterRecoversWhenAnchorWasTheOutlier() {
        val f = GpsPreFilter()
        f.check(0, 12.01, 77.0, 5f, null)   // outlier accepted as the first fix
        val verdicts = (1..8).map { f.check(it * 1000L, 12.0 + it * 0.00003, 77.0, 5f, null) }
        assertThat(verdicts.take(AnalysisTuning.MAX_CONSECUTIVE_SPEED_REJECTS).all { it == FixVerdict.TOO_FAST }).isTrue()
        assertThat(verdicts.last()).isEqualTo(FixVerdict.ACCEPTED)
    }

    // ---- Steps -------------------------------------------------------------------------------

    @Test fun stepTimelineFromDetectorExcludesPauses() {
        val samples = (1..600).map { SensorSample(SensorType.STEP_DETECTOR, it * 375L, 1.0) }   // 160 spm for 225 s
        val tl = StepTimeline.from(samples, listOf(PauseInterval(60_000, 120_000)))
        assertThat(tl.hasData).isTrue()
        assertThat(tl.cadence(30_000, 90_000)).isWithin(1.0).of(160.0)
        assertThat(tl.stepsBetween(0, 225_000)).isWithin(2.0).of(600.0 - 160.0)
    }

    @Test fun stepTimelineFromCounterToleratesReset() {
        val samples = listOf(
            SensorSample(SensorType.STEP_COUNTER, 0, 5_000.0), SensorSample(SensorType.STEP_COUNTER, 60_000, 5_170.0),
            SensorSample(SensorType.STEP_COUNTER, 120_000, 20.0), SensorSample(SensorType.STEP_COUNTER, 180_000, 190.0),
        )
        val tl = StepTimeline.from(samples)
        assertThat(tl.stepsBetween(0, 180_000)).isWithin(0.1).of(340.0)
        assertThat(tl.at(30_000)).isWithin(0.1).of(85.0)
        assertThat(StepTimeline.from(emptyList()).hasData).isFalse()
    }

    // ---- Stride, gait, calories -------------------------------------------------------------

    @Test fun strideSeededFromHeightAndLearned() {
        val p = RunnerProfile(heightCm = 180)
        assertThat(StrideModel.seed(p, Gait.RUN)).isWithin(0.01).of(1.134)
        assertThat(StrideModel.seed(p, Gait.WALK)).isWithin(0.01).of(0.747)
        assertThat(StrideModel.prior(p.copy(learnedRunStrideM = 1.05), Gait.RUN)).isEqualTo(1.05)
        assertThat(StrideModel.seed(RunnerProfile(), Gait.RUN)).isWithin(0.01).of(1.071)  // default 170 cm
        assertThat(StrideModel.measure(Gait.RUN, 1_100.0, 1_000.0)).isWithin(1e-9).of(1.1)
        assertThat(StrideModel.measure(Gait.RUN, 100.0, 50.0)).isNull()           // too few steps
        assertThat(StrideModel.measure(Gait.WALK, 3_000.0, 1_000.0)).isNull()     // implausible 3 m walking stride
        assertThat(StrideModel.learn(null, 1.1)).isEqualTo(1.1)
        assertThat(StrideModel.learn(1.0, null)).isEqualTo(1.0)
        assertThat(StrideModel.learn(1.0, 1.1)!!).isWithin(1e-9).of(1.03)
    }

    @Test fun gaitFromCadenceThenSpeed() {
        assertThat(GaitClassifier.classify(165.0, 1.8)).isEqualTo(Gait.RUN)     // slow jog, running cadence
        assertThat(GaitClassifier.classify(115.0, 2.3)).isEqualTo(Gait.WALK)    // fast walker
        assertThat(GaitClassifier.classify(132.0, 2.5)).isEqualTo(Gait.RUN)     // ambiguous cadence, speed decides
        assertThat(GaitClassifier.classify(null, 2.5)).isEqualTo(Gait.RUN)
        assertThat(GaitClassifier.classify(null, 1.5)).isEqualTo(Gait.WALK)
    }

    @Test fun caloriesDependOnWeightAgeSexAndGait() {
        val male = RunnerProfile(weightKg = 70.0, ageYears = 30, sex = Sex.MALE, heightCm = 175)
        assertThat(Calories.bmrKcalPerDay(male)).isWithin(0.01).of(1_648.75)
        val run5k = Calories.activityKcal(male, Gait.RUN, 5_000.0, 0.0) + Calories.restingKcal(male, 27 * 60_000L + 30_000)
        assertThat(run5k).isWithin(5.0).of(381.0)
        val female = male.copy(sex = Sex.FEMALE)
        assertThat(Calories.restingKcal(female, 3_600_000)).isLessThan(Calories.restingKcal(male, 3_600_000))
        assertThat(Calories.restingKcal(male.copy(ageYears = 60), 3_600_000)).isLessThan(Calories.restingKcal(male, 3_600_000))
        assertThat(Calories.activityKcal(male, Gait.WALK, 1_000.0, 0.0)).isWithin(1e-9).of(Calories.activityKcal(male, Gait.RUN, 1_000.0, 0.0) / 2)
        assertThat(Calories.activityKcal(male, Gait.RUN, 1_000.0, 40.0)).isGreaterThan(Calories.activityKcal(male, Gait.RUN, 1_000.0, 0.0))
        assertThat(Calories.activityKcal(male.copy(weightKg = 90.0), Gait.RUN, 1_000.0, 0.0)).isGreaterThan(Calories.activityKcal(male, Gait.RUN, 1_000.0, 0.0))
    }

    // ---- Elevation ----------------------------------------------------------------------------

    @Test fun barometricAltitude() {
        assertThat(pressureToAltitudeM(1_013.25)).isWithin(0.01).of(0.0)
        assertThat(pressureToAltitudeM(899.0)).isWithin(5.0).of(1_000.0)
    }

    @Test fun climbCounterIgnoresNoiseBelowThreshold() {
        val c = ClimbCounter(1.0)
        listOf(100.0, 100.4, 99.7, 100.3, 101.2, 102.5, 102.1, 103.6, 101.0).forEach { c.add(it) }
        assertThat(c.gain).isWithin(1e-9).of(3.6)
        assertThat(c.loss).isWithin(1e-9).of(2.6)
        c.restart(); c.add(50.0)
        assertThat(c.loss).isWithin(1e-9).of(2.6)
    }

    @Test fun elevationPrefersBarometerAndAnchorsToGps() {
        val times = LongArray(100) { it * 1000L }
        val gps = Array<Double?>(100) { 950.0 }
        val pressure = (0 until 100).map { it * 1000L to 1_013.25 * Math.pow(1 - (100.0 + it * 0.1) / 44_330.77, 1 / 0.190263) }
        val baro = ElevationBuilder.build(times, gps, pressure)
        assertThat(baro.source).isEqualTo(ElevationSource.BAROMETER)
        assertThat(baro.values!![50]).isWithin(1.0).of(950.0)   // anchored to the GPS median
        assertThat(baro.values!![99] - baro.values!![0]).isWithin(1.5).of(9.9)
        val gpsOnly = ElevationBuilder.build(times, gps, emptyList())
        assertThat(gpsOnly.source).isEqualTo(ElevationSource.GPS)
        assertThat(ElevationBuilder.build(times, arrayOfNulls(100), emptyList()).source).isEqualTo(ElevationSource.NONE)
    }

    // ---- Timeline: splits, best efforts, segments ---------------------------------------------

    /** 1 Hz timeline at [speed] with a stop between [stopFrom] and [stopTo] seconds and walk seconds [walk]. */
    private fun timeline(seconds: Int, speed: Double, stop: IntRange? = null, walk: IntRange? = null, fast: IntRange? = null): Timeline {
        val n = seconds + 1
        val d = DoubleArray(n)
        val m = DoubleArray(n)
        val kind = arrayOfNulls<SegmentKind>(n)
        for (i in 1 until n) {
            kind[i] = when {
                stop != null && i in stop -> SegmentKind.STOP
                walk != null && i in walk -> SegmentKind.WALK
                else -> SegmentKind.RUN
            }
            val v = when (kind[i]) { SegmentKind.STOP -> 0.0; SegmentKind.WALK -> 1.5; else -> if (fast != null && i in fast) speed * 1.25 else speed }
            d[i] = d[i - 1] + v
            m[i] = m[i - 1] + if (kind[i] == SegmentKind.STOP) 0.0 else 1000.0
        }
        return Timeline(LongArray(n) { it * 1000L }, d, m, kind, DoubleArray(n) { if (it == 0) 0.0 else 2.8 },
            DoubleArray(n) { if (it % 100 == 0) 1.0 else 0.0 }, DoubleArray(n))
    }

    @Test fun splitsUseMovingTimeAndInterpolateBoundaries() {
        val tl = timeline(seconds = 900, speed = 2.5, stop = 100..139)   // ~2152 m with a 40 s stop
        val splits = SplitCalculator.compute(tl, hasElevation = true, hasSteps = true)
        assertThat(splits).hasSize(3)
        assertThat(splits[0].distanceM).isWithin(1e-6).of(1_000.0)
        assertThat(splits[0].movingMillis).isEqualTo(400_000L)
        assertThat(splits[0].elapsedMillis).isEqualTo(440_000L)
        assertThat(splits[0].paceSecPerKm!!).isWithin(0.01).of(400.0)
        assertThat(splits[1].startMillis).isEqualTo(splits[0].endMillis)
        assertThat(splits[2].distanceM).isWithin(1e-6).of(tl.totalDistance - 2_000.0)
        assertThat(splits.sumOf { it.elevationGainM!! }).isWithin(1e-6).of(9.0)
        assertThat(splits[0].avgCadenceSpm!!).isWithin(1.0).of(2.8 * 440 / 400 * 60)
        assertThat(splits[0].walkFraction).isEqualTo(0.0)
    }

    @Test fun shortTrailingSplitDropped() {
        val tl = timeline(seconds = 402, speed = 2.5)   // 1005 m
        assertThat(SplitCalculator.compute(tl, hasElevation = false, hasSteps = false)).hasSize(1)
        assertThat(SplitCalculator.compute(tl, false, false)[0].elevationGainM).isNull()
    }

    @Test fun bestEffortsFindFastestMovingWindow() {
        val tl = timeline(seconds = 2_400, speed = 2.5, stop = 500..559, fast = 1_200..1_519)  // 320 s at 3.125 m/s = 1000 m
        val efforts = BestEffortCalculator.compute(tl).associateBy { it.distance }
        assertThat(efforts.keys).containsExactly(BestEffortDistance.K1, BestEffortDistance.K5)
        assertThat(efforts[BestEffortDistance.K1]!!.durationMillis).isEqualTo(320_000L)
        assertThat(efforts[BestEffortDistance.K1]!!.startMillis).isEqualTo(1_199_000L)
        // The stop is excluded: 5K moving time = 1000 m fast + 4000 m at 2.5 m/s.
        assertThat(efforts[BestEffortDistance.K5]!!.durationMillis.toDouble()).isWithin(1.0).of(320_000.0 + 1_600_000.0)
    }

    @Test fun segmentsCoverTimelineAndSplitAtPauses() {
        val tl = timeline(seconds = 300, speed = 3.0, stop = 50..69, walk = 150..209)
        tl.kind[250] = null   // a pause boundary
        val segs = SegmentBuilder.compute(tl, hasSteps = true)
        assertThat(segs.map { it.kind }).containsExactly(SegmentKind.RUN, SegmentKind.STOP, SegmentKind.RUN, SegmentKind.WALK, SegmentKind.RUN, SegmentKind.RUN).inOrder()
        assertThat(segs[1].endMillis - segs[1].startMillis).isEqualTo(20_000L)
        assertThat(segs[1].startDistanceM).isEqualTo(segs[1].endDistanceM)
        assertThat(segs[3].avgSpeedMps!!).isWithin(1e-9).of(1.5)
        assertThat(segs[4].endMillis).isEqualTo(249_000L)
        assertThat(segs[5].startMillis).isEqualTo(250_000L)
    }

    // ---- Namer + series -------------------------------------------------------------------------

    @Test fun namerUsesLocalHourBuckets() {
        val namer = DefaultRunNamer()
        fun at(h: Int) = LocalDateTime.of(2026, 11, 24, h, 30).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        assertThat(namer.name(at(6), "Asia/Kolkata", ActivityType.RUN)).isEqualTo("Morning Run")
        assertThat(namer.name(at(12), "Asia/Kolkata", ActivityType.WALK)).isEqualTo("Lunch Walk")
        assertThat(namer.name(at(15), "Asia/Kolkata", ActivityType.RUN)).isEqualTo("Afternoon Run")
        assertThat(namer.name(at(19), "Asia/Kolkata", ActivityType.RUN)).isEqualTo("Evening Run")
        assertThat(namer.name(at(23), "Asia/Kolkata", ActivityType.WALK)).isEqualTo("Night Walk")
        assertThat(namer.name(at(4), "Asia/Kolkata", ActivityType.RUN)).isEqualTo("Night Run")
        // Same instant in another zone gives that zone's local hour; a bad zone falls back to UTC.
        assertThat(namer.name(at(6), "Europe/London", ActivityType.RUN)).isEqualTo("Night Run")
        assertThat(namer.name(at(6), "Not/AZone", ActivityType.RUN)).isEqualTo("Night Run")
    }

    private fun point(t: Long, d: Double, pace: Double?, elev: Double?, kind: SegmentKind) = TrackPoint(
        runId = "r", kind = PointKind.FILTERED, engine = null, isPrimary = true, timeMillis = t, elapsedRealtimeNanos = null,
        lat = 0.0, lon = 0.0, altitudeM = null, mslAltitudeM = null, horizontalAccuracyM = null, verticalAccuracyM = null,
        speedMps = null, speedAccuracyMps = null, bearingDeg = null, paused = false,
        distanceM = d, elevationM = elev, paceSecPerKm = pace, segmentKind = kind,
    )

    @Test fun seriesBuilderRemovesStopsAndDownsamples() {
        val pts = (0 until 2_000).map { i ->
            val stop = i in 500..540
            point(i * 1000L, if (stop) 1_500.0 else i * 3.0, if (stop) null else 333.0 + (i % 3), 900.0 + i * 0.01, if (stop) SegmentKind.STOP else SegmentKind.RUN)
        }
        val s = DefaultRunSeriesBuilder(maxPoints = 300).build(pts)
        assertThat(s.pace.size).isAtMost(301)
        assertThat(s.pace.all { it.value in 333.0..335.0 }).isTrue()
        assertThat(s.pace.map { it.distanceKm }).isInOrder()
        assertThat(s.elevation.last().value).isWithin(1.0).of(920.0)
        assertThat(s.cadence).isEmpty()
        val steps = (1..3_000).map { SensorSample(SensorType.STEP_DETECTOR, it * 375L, 1.0) }
        val withCadence = DefaultRunSeriesBuilder().build(pts, steps)
        assertThat(withCadence.cadence).isNotEmpty()
        assertThat(withCadence.cadence[50].value).isWithin(2.0).of(160.0)
    }
}
