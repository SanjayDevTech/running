package com.example.running.ui.rundetail

import com.example.running.core.model.BestEffort
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.BestEffortWithRank
import com.example.running.core.model.Segment
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.SeriesPoint
import com.example.running.core.model.Split
import com.example.running.ui.components.charts.ChartPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RunDetailMapperTest {
    private fun split(i: Int, dist: Double = 1000.0, pace: Double? = 300.0) = Split(
        index = i, distanceM = dist, startMillis = 0, endMillis = 0, movingMillis = 0, elapsedMillis = 0,
        paceSecPerKm = pace, elevationGainM = 5.0, elevationLossM = 2.0, avgCadenceSpm = null, walkFraction = null,
    )

    private fun seg(kind: SegmentKind, ms: Long, d0: Double, d1: Double) =
        Segment(kind, 0, ms, d0, d1, avgSpeedMps = 3.0, avgCadenceSpm = null)

    @Test fun splitsMarkFastestAndScaleBars() {
        val rows = buildSplitRows(listOf(split(0, pace = 320.0), split(1, pace = 300.0), split(2, dist = 420.0, pace = 280.0)))
        assertThat(rows.map { it.label }).containsExactly("1", "2", "0.42").inOrder()
        assertThat(rows[1].isFastest).isTrue()
        assertThat(rows[2].isFastest).isFalse() // partial last split never wins the highlight
        assertThat(rows[1].barFraction).isWithin(0.001f).of(1f)
        assertThat(rows[0].barFraction).isLessThan(1f)
        assertThat(rows[1].startDistanceM).isEqualTo(1000.0)
        assertThat(rows[2].endDistanceM).isEqualTo(2420.0)
    }

    @Test fun singleSplitIsNotHighlighted() {
        assertThat(buildSplitRows(listOf(split(0))).single().isFastest).isFalse()
        assertThat(buildSplitRows(emptyList())).isEmpty()
    }

    @Test fun unknownPaceHasNoBar() {
        val rows = buildSplitRows(listOf(split(0, pace = null), split(1, pace = 300.0)))
        assertThat(rows[0].barFraction).isEqualTo(0f)
        assertThat(rows[0].pace).isEqualTo("--:--")
    }

    @Test fun segmentsOnlyForMixedActivities() {
        assertThat(buildSegments(listOf(seg(SegmentKind.RUN, 600_000, 0.0, 2000.0)))).isNull()
        val mixed = buildSegments(
            listOf(
                seg(SegmentKind.RUN, 600_000, 0.0, 2000.0),
                seg(SegmentKind.WALK, 120_000, 2000.0, 2200.0),
                seg(SegmentKind.RUN, 300_000, 2200.0, 3200.0),
            ),
        )!!
        assertThat(mixed.runMillis).isEqualTo(900_000)
        assertThat(mixed.walkMillis).isEqualTo(120_000)
        assertThat(mixed.rows).hasSize(3)
        assertThat(mixed.rows[1].distanceKm).isEqualTo("0.20")
    }

    @Test fun effortsOrderedByDistanceWithPbFlag() {
        fun e(d: BestEffortDistance, rank: Int) = BestEffortWithRank(BestEffort(d, 1_500_000, 0, 0, 0.0), rank)
        val out = buildEfforts(listOf(e(BestEffortDistance.K10, 2), e(BestEffortDistance.K1, 1)))
        assertThat(out.map { it.label }).containsExactly("1K", "10K").inOrder()
        assertThat(out.map { it.isPersonalBest }).containsExactly(true, false).inOrder()
        assertThat(out[0].time).isEqualTo("25:00")
    }

    @Test fun downsampleKeepsShortSeriesAndThinsLongOnes() {
        val short = (0 until 10).map { ChartPoint(it.toDouble(), it.toDouble()) }
        assertThat(downsample(short, 300)).isEqualTo(short)
        val long = (0 until 1000).map { ChartPoint(it.toDouble(), 5.0) }
        val thin = downsample(long, 100)
        assertThat(thin).hasSize(100)
        assertThat(thin.all { it.y == 5.0 }).isTrue()
        assertThat(thin.first().x).isLessThan(thin.last().x)
    }

    @Test fun paceChartDropsStandingAndNonFinite() {
        val pts = paceChartPoints(
            listOf(SeriesPoint(0.1, 300.0), SeriesPoint(0.2, 5000.0), SeriesPoint(0.3, Double.NaN), SeriesPoint(0.4, 310.0)),
        )
        assertThat(pts.map { it.y }).containsExactly(300.0, 310.0).inOrder()
    }
}
