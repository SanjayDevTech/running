package com.example.running.ui.activities

import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.ActivityType
import com.example.running.core.model.MapRoute
import com.example.running.core.model.Run
import com.example.running.core.model.TrackPoint
import com.example.running.core.stats.bucketByDay
import com.example.running.core.stats.totals
import com.example.running.core.util.Clock
import com.example.running.core.util.formatDistanceKm
import com.example.running.core.util.formatDuration
import com.example.running.core.util.formatDurationShort
import com.example.running.core.util.formatElevation
import com.example.running.core.util.formatPace
import com.example.running.core.util.formatRelativeDay
import com.example.running.core.util.startOfWeek
import com.example.running.core.util.weekRange
import java.time.Instant
import java.time.ZoneId

internal data class RunCardUi(
    val id: String,
    val name: String,
    val type: ActivityType,
    val whenText: String,
    val distanceKm: String,
    val duration: String,
    val pace: String,
)

/** One bar of the "This week" strip (Monday..Sunday). [fraction] is relative to the busiest day. */
internal data class DayBarUi(val dayIndex: Int, val fraction: Float, val isToday: Boolean, val hasActivity: Boolean)

internal data class WeekSummaryUi(
    val count: Int,
    val distanceKm: String,
    val duration: String,
    val elevation: String,
    val days: List<DayBarUi>,
)

internal data class ActivitiesUiState(
    val filter: ActivityFilter = ActivityFilter.ALL,
    val loading: Boolean = true,
    val week: WeekSummaryUi? = null,
    val cards: List<RunCardUi> = emptyList(),
)

internal fun buildWeekSummary(runs: List<Run>, nowMillis: Long, zone: ZoneId): WeekSummaryUi {
    val week = weekRange(nowMillis, zone)
    val days = bucketByDay(runs, startOfWeek(nowMillis, zone), zone)
    val total = runs.totals(week.first, week.last + 1)
    val maxDistance = days.maxOfOrNull { it.distanceM } ?: 0.0
    val todayIndex = Instant.ofEpochMilli(nowMillis).atZone(zone).dayOfWeek.value - 1 // Monday = 0
    return WeekSummaryUi(
        count = total.count,
        distanceKm = formatDistanceKm(total.distanceM, 1),
        duration = formatDurationShort(total.movingMillis),
        elevation = formatElevation(total.elevationGainM),
        days = days.mapIndexed { i, d ->
            DayBarUi(
                dayIndex = i,
                fraction = if (maxDistance > 0.0) (d.distanceM / maxDistance).toFloat() else 0f,
                isToday = i == todayIndex,
                hasActivity = d.count > 0,
            )
        },
    )
}

internal fun Run.toCard(clock: Clock): RunCardUi = RunCardUi(
    id = id,
    name = name,
    type = type,
    whenText = formatRelativeDay(startedAtMillis, zoneId, clock),
    distanceKm = formatDistanceKm(stats.distanceM),
    duration = formatDuration(stats.movingMillis),
    pace = formatPace(stats.avgPaceSecPerKm),
)

internal fun buildActivitiesState(filter: ActivityFilter, runs: List<Run>, clock: Clock): ActivitiesUiState =
    ActivitiesUiState(
        filter = filter,
        loading = false,
        week = buildWeekSummary(runs, clock.nowMillis(), clock.zoneId()),
        cards = runs.map { it.toCard(clock) },
    )

/** Cheap list-safe route: evenly thinned to at most [maxPoints] points (always keeps first and last). */
internal fun thumbnailRoute(points: List<TrackPoint>, maxPoints: Int = 100): MapRoute? {
    if (points.size < 2) return null
    val step = ((points.size - 1).toDouble() / (maxPoints - 1)).coerceAtLeast(1.0)
    val picked = ArrayList<TrackPoint>()
    var pos = 0.0
    while (pos < points.size - 1) {
        picked += points[pos.toInt()]
        pos += step
    }
    picked += points.last()
    return MapRoute.fromTrackPoints(picked)
}
