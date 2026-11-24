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

/**
 * Y label step for a chart whose tallest bar is [axisMax], about [AXIS_LABELS] labels on a round
 * value that [formatAxis] prints apart: whole runs, whole minutes, whole metres, 0.1 km (1 km once
 * the axis shows whole kilometres).
 */
internal fun ProgressMetric.axisStep(axisMax: Double): Double {
    val wanted = axisMax.coerceAtLeast(0.0) / AXIS_LABELS
    return when (this) {
        ProgressMetric.DISTANCE -> maxOf(niceStep(wanted), if (axisMax < 10_000.0) 100.0 else 1_000.0)
        ProgressMetric.TIME -> TIME_STEPS_MIN.firstOrNull { it * 60_000.0 >= wanted }?.let { it * 60_000.0 }
            ?: (Math.ceil(wanted / 3_600_000.0) * 3_600_000.0)
        ProgressMetric.RUNS -> maxOf(niceStep(wanted), 1.0)
        ProgressMetric.ELEVATION -> maxOf(niceStep(wanted), 1.0)
    }
}

private const val AXIS_LABELS = 4
private val TIME_STEPS_MIN = listOf(1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 180.0, 300.0, 600.0)

/** 1-2-5 value at or above [raw]. */
private fun niceStep(raw: Double): Double {
    if (!raw.isFinite() || raw <= 0.0) return 0.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)))
    return listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= raw - 1e-9 * magnitude } * magnitude
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
