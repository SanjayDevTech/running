package com.example.running.ui.progress

import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.Run
import com.example.running.core.stats.PeriodTotals
import com.example.running.core.stats.bucketByMonth
import com.example.running.core.stats.bucketByWeek
import com.example.running.core.stats.totals
import com.example.running.core.util.formatDistanceKm
import com.example.running.core.util.formatDurationShort
import com.example.running.core.util.formatElevation
import com.example.running.ui.components.charts.BarDatum
import java.time.ZoneId

internal enum class ProgressPeriod(val buckets: Int) { WEEKLY(12), MONTHLY(12) }

internal enum class ProgressMetric { DISTANCE, TIME, RUNS, ELEVATION }

/** Chart value of one bucket: metres, moving millis, count, or elevation metres. */
internal fun ProgressMetric.value(t: PeriodTotals): Double = when (this) {
    ProgressMetric.DISTANCE -> t.distanceM
    ProgressMetric.TIME -> t.movingMillis.toDouble()
    ProgressMetric.RUNS -> t.count.toDouble()
    ProgressMetric.ELEVATION -> t.elevationGainM
}

/** Axis label for a raw chart value of this metric. [axisMax] picks distance decimals. */
internal fun ProgressMetric.formatAxis(value: Double, axisMax: Double): String = when (this) {
    ProgressMetric.DISTANCE -> formatDistanceKm(value, if (axisMax < 10_000.0) 1 else 0)
    ProgressMetric.TIME -> formatDurationShort(value.toLong())
    ProgressMetric.RUNS -> Math.round(value).toString()
    ProgressMetric.ELEVATION -> formatElevation(value)
}

internal data class ProgressUiState(
    val filter: ActivityFilter,
    val period: ProgressPeriod,
    val metric: ProgressMetric,
    val buckets: List<PeriodTotals>,
    val bars: List<BarDatum>,
    val selectedIndex: Int,
    val axisMax: Double,
    val selected: PeriodTotals,
    val allTime: PeriodTotals,
    val streaks: Streaks,
    val recent: List<Run>,
) {
    val isEmpty: Boolean get() = allTime.count == 0
    val isCurrentSelected: Boolean get() = selectedIndex == buckets.lastIndex
}

internal const val RECENT_COUNT = 5

/** Pure: everything the Progress screen shows, from the finished runs of one filter. */
internal fun buildProgressState(
    runs: List<Run>,
    filter: ActivityFilter,
    period: ProgressPeriod,
    metric: ProgressMetric,
    selectedIndex: Int?,
    nowMillis: Long,
    zone: ZoneId,
): ProgressUiState {
    val buckets = when (period) {
        ProgressPeriod.WEEKLY -> bucketByWeek(runs, period.buckets, nowMillis, zone)
        ProgressPeriod.MONTHLY -> bucketByMonth(runs, period.buckets, nowMillis, zone)
    }
    val index = (selectedIndex ?: buckets.lastIndex).coerceIn(0, buckets.lastIndex)
    val labels = chartLabels(buckets, period, zone)
    val values = buckets.map { metric.value(it) }
    return ProgressUiState(
        filter = filter, period = period, metric = metric, buckets = buckets,
        bars = labels.zip(values) { l, v -> BarDatum(l, v) },
        selectedIndex = index,
        axisMax = values.maxOrNull() ?: 0.0,
        selected = buckets[index],
        allTime = runs.totals(Long.MIN_VALUE, Long.MAX_VALUE),
        streaks = computeStreaks(runs, nowMillis, zone),
        recent = runs.sortedByDescending { it.startedAtMillis }.take(RECENT_COUNT),
    )
}
