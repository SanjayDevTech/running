package com.example.running.ui.rundetail

import com.example.running.core.model.ActivityType
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.BestEffortWithRank
import com.example.running.core.model.MapRoute
import com.example.running.core.model.Run
import com.example.running.core.model.RunStats
import com.example.running.core.model.Segment
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.SeriesPoint
import com.example.running.core.model.Split
import com.example.running.core.util.MAX_CHART_PACE_SEC_PER_KM
import com.example.running.core.util.METERS_PER_KM
import com.example.running.core.util.formatCadence
import com.example.running.core.util.formatCalories
import com.example.running.core.util.formatDistanceKm
import com.example.running.core.util.formatDuration
import com.example.running.core.util.formatElevation
import com.example.running.core.util.formatPace
import com.example.running.core.util.formatRunDateTime
import com.example.running.core.util.paceSecPerKm
import com.example.running.ui.components.charts.ChartPoint
import com.example.running.ui.components.charts.splitAtBreaks

/** Pre-formatted headline numbers (everything goes through core/util/Format.kt). */
internal data class HeadlineUi(
    val distanceKm: String,
    val movingTime: String,
    val pace: String,
    val elapsed: String,
    val elevation: String,
    val calories: String,
    val cadence: String,
)

internal data class SplitRowUi(
    val index: Int,
    val label: String,
    val pace: String,
    /** 1.0 = fastest split; shorter bars are slower. 0 when pace is unknown. */
    val barFraction: Float,
    val elevation: String,
    val isFastest: Boolean,
    val startDistanceM: Double,
    val endDistanceM: Double,
)

internal data class SegmentRowUi(
    val kind: SegmentKind,
    val duration: String,
    val distanceKm: String,
    val pace: String,
)

internal data class SegmentsUi(
    val rows: List<SegmentRowUi>,
    val runMillis: Long,
    val walkMillis: Long,
)

internal data class EffortUi(
    val distance: BestEffortDistance,
    val label: String,
    val time: String,
    val rank: Int,
    val isPersonalBest: Boolean,
)

/** Route and chart series derived from the FILTERED points; loaded off the main thread. */
internal data class RunGeometry(
    val route: MapRoute?,
    val pace: List<ChartPoint>,
    val elevation: List<ChartPoint>,
    /** Distances (km) where the pace line breaks (recording gaps). */
    val paceBreaksKm: List<Double> = emptyList(),
) {
    companion object { val EMPTY = RunGeometry(null, emptyList(), emptyList()) }
}

internal data class RunDetailContent(
    val run: Run,
    val subtitle: String,
    val headline: HeadlineUi,
    val geometry: RunGeometry,
    val splits: List<SplitRowUi>,
    val segments: SegmentsUi?,
    val efforts: List<EffortUi>,
    val showDevCompare: Boolean,
    /** The type the "Change type" menu entry switches to. */
    val otherType: ActivityType,
)

internal sealed interface RunDetailUiState {
    data object Loading : RunDetailUiState
    data object NotFound : RunDetailUiState
    data class Content(val content: RunDetailContent) : RunDetailUiState
}

internal fun RunStats.toHeadline() = HeadlineUi(
    distanceKm = formatDistanceKm(distanceM),
    movingTime = formatDuration(movingMillis),
    pace = formatPace(avgPaceSecPerKm),
    elapsed = formatDuration(elapsedMillis),
    elevation = formatElevation(elevationGainM),
    calories = formatCalories(caloriesKcal),
    cadence = formatCadence(avgCadenceSpm),
)

/** A split is "full" when it covers a whole km (the last one is usually partial). */
private fun Split.isFull() = distanceM >= METERS_PER_KM - 1.0

internal fun buildSplitRows(splits: List<Split>): List<SplitRowUi> {
    if (splits.isEmpty()) return emptyList()
    val fullPaces = splits.filter { it.isFull() }.mapNotNull { it.paceSecPerKm }
    val fastest = (fullPaces.ifEmpty { splits.mapNotNull { it.paceSecPerKm } }).minOrNull()
    val highlightFastest = fullPaces.size >= 2
    var cumulative = 0.0
    return splits.sortedBy { it.index }.map { s ->
        val start = cumulative
        cumulative += s.distanceM
        val pace = s.paceSecPerKm
        SplitRowUi(
            index = s.index,
            label = if (s.isFull()) (s.index + 1).toString() else formatDistanceKm(s.distanceM),
            pace = formatPace(pace),
            barFraction = if (pace != null && fastest != null && pace > 0) (fastest / pace).toFloat().coerceIn(0.05f, 1f) else 0f,
            elevation = formatElevation(s.elevationGainM),
            isFastest = highlightFastest && s.isFull() && pace != null && pace == fastest,
            startDistanceM = start,
            endDistanceM = cumulative,
        )
    }
}

/** Null when the run has no walking (or no running): the section only helps for mixed activities. */
internal fun buildSegments(segments: List<Segment>): SegmentsUi? {
    val moving = segments.filter { it.endMillis > it.startMillis }
    val hasRun = moving.any { it.kind == SegmentKind.RUN }
    val hasWalk = moving.any { it.kind == SegmentKind.WALK }
    if (!(hasRun && hasWalk)) return null
    fun total(kind: SegmentKind) = moving.filter { it.kind == kind }.sumOf { it.endMillis - it.startMillis }
    return SegmentsUi(
        rows = moving.map { s ->
            SegmentRowUi(
                kind = s.kind,
                duration = formatDuration(s.endMillis - s.startMillis),
                distanceKm = formatDistanceKm(s.endDistanceM - s.startDistanceM),
                pace = if (s.kind == SegmentKind.STOP) formatPace(null) else formatPace(s.avgSpeedMps?.let { paceSecPerKm(it) }),
            )
        },
        runMillis = total(SegmentKind.RUN),
        walkMillis = total(SegmentKind.WALK),
    )
}

internal fun buildEfforts(efforts: List<BestEffortWithRank>): List<EffortUi> =
    efforts.sortedBy { it.effort.distance.ordinal }.map {
        EffortUi(
            distance = it.effort.distance,
            label = it.effort.distance.label,
            time = formatDuration(it.effort.durationMillis),
            rank = it.rank,
            isPersonalBest = it.isPersonalBest,
        )
    }

/**
 * Thins [points] to at most [maxPoints] by averaging equal-size buckets, so long runs stay smooth and
 * cheap to chart. Non-finite points are dropped first.
 */
internal fun downsample(points: List<ChartPoint>, maxPoints: Int = 300): List<ChartPoint> {
    val clean = points.filter { it.x.isFinite() && it.y.isFinite() }
    if (clean.size <= maxPoints || maxPoints < 2) return clean
    val bucket = clean.size.toDouble() / maxPoints
    return (0 until maxPoints).map { i ->
        val from = (i * bucket).toInt()
        val to = ((i + 1) * bucket).toInt().coerceIn(from + 1, clean.size)
        val slice = clean.subList(from, to)
        ChartPoint(slice.sumOf { it.x } / slice.size, slice.sumOf { it.y } / slice.size)
    }
}

/**
 * Pace chart: drops standing/implausible paces (slower than 30 min/km) before thinning. With [breaksKm]
 * each stretch between breaks is thinned on its own, so no averaged point bridges a gap.
 */
internal fun paceChartPoints(series: List<SeriesPoint>, maxPoints: Int = 300, breaksKm: List<Double> = emptyList()): List<ChartPoint> {
    val clean = series.filter { it.value > 0.0 && it.value <= MAX_CHART_PACE_SEC_PER_KM }.map { ChartPoint(it.distanceKm, it.value) }
    if (breaksKm.isEmpty()) return downsample(clean, maxPoints)
    return splitAtBreaks(clean, breaksKm).flatMap { section ->
        downsample(section, maxOf(2, maxPoints * section.size / clean.size.coerceAtLeast(1)))
    }
}

internal fun elevationChartPoints(series: List<SeriesPoint>, maxPoints: Int = 300): List<ChartPoint> =
    downsample(series.map { ChartPoint(it.distanceKm, it.value) }, maxPoints)

internal fun buildRunDetailContent(
    run: Run,
    geometry: RunGeometry,
    splits: List<Split>,
    segments: List<Segment>,
    efforts: List<BestEffortWithRank>,
    devModeUnlocked: Boolean,
): RunDetailContent = RunDetailContent(
    run = run,
    subtitle = formatRunDateTime(run.startedAtMillis, run.zoneId),
    headline = run.stats.toHeadline(),
    geometry = geometry,
    splits = buildSplitRows(splits),
    segments = buildSegments(segments),
    efforts = buildEfforts(efforts),
    showDevCompare = devModeUnlocked && run.devDualEngine,
    otherType = if (run.type == ActivityType.RUN) ActivityType.WALK else ActivityType.RUN,
)
