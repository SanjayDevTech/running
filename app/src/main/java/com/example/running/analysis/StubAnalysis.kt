package com.example.running.analysis

import com.example.running.core.contracts.LiveMetrics
import com.example.running.core.contracts.LiveMetricsFactory
import com.example.running.core.contracts.RunAnalyzer
import com.example.running.core.contracts.RunNamer
import com.example.running.core.contracts.RunSeriesBuilder
import com.example.running.core.model.ActivityType
import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.ElevationSource
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.LiveStats
import com.example.running.core.model.LocationFix
import com.example.running.core.model.MapRoute
import com.example.running.core.model.PointKind
import com.example.running.core.model.PressureSample
import com.example.running.core.model.RoutePoint
import com.example.running.core.model.RunAnalysis
import com.example.running.core.model.RunSeries
import com.example.running.core.model.RunStats
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.Segment
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.SeriesPoint
import com.example.running.core.model.Split
import com.example.running.core.model.StepEvent
import com.example.running.core.model.TrackPoint
import com.example.running.core.util.METERS_PER_KM
import com.example.running.core.util.haversineMeters
import com.example.running.core.util.paceSecPerKm
import java.time.Instant
import java.time.ZoneId

// FOUNDATION stubs (pure Kotlin). Track B replaces all of this; keep the package pure Kotlin.

internal class StubRunNamer : RunNamer {
    override fun name(startedAtMillis: Long, zoneId: String, type: ActivityType): String {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC"))
        val hour = Instant.ofEpochMilli(startedAtMillis).atZone(zone).hour
        val part = when (hour) {
            in 5..10 -> "Morning"
            in 11..13 -> "Lunch"
            in 14..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
        return "$part ${if (type == ActivityType.WALK) "Walk" else "Run"}"
    }
}

internal class StubRunAnalyzer(private val namer: RunNamer) : RunAnalyzer {
    override fun analyze(input: AnalysisInput): RunAnalysis {
        val raw = input.rawPoints.sortedBy { it.timeMillis }
        var cumulative = 0.0
        var prev: TrackPoint? = null
        val filtered = raw.map { p ->
            val last = prev
            if (last != null && !p.paused && !last.paused) {
                cumulative += haversineMeters(last.lat, last.lon, p.lat, p.lon)
            }
            prev = p
            p.copy(kind = PointKind.FILTERED, distanceM = cumulative, segmentKind = SegmentKind.RUN)
        }
        val elapsed = (input.endedAtMillis - input.startedAtMillis).coerceAtLeast(0)
        val paused = input.pauses.sumOf { ((it.endMillis ?: input.endedAtMillis) - it.startMillis).coerceAtLeast(0) }
            .coerceAtMost(elapsed)
        val moving = elapsed - paused
        val type = input.manualType ?: ActivityType.RUN
        val stats = RunStats(
            distanceM = cumulative,
            elapsedMillis = elapsed,
            movingMillis = moving,
            pausedMillis = paused,
            avgPaceSecPerKm = paceSecPerKm(cumulative, moving),
            elevationSource = ElevationSource.NONE,
            bounds = GeoBounds.of(raw.map { GeoPoint(it.lat, it.lon) }),
            weightKgUsed = input.profile.weightKg,
        )
        val segments = if (filtered.isEmpty()) emptyList() else listOf(
            Segment(
                kind = SegmentKind.RUN, startMillis = input.startedAtMillis, endMillis = input.endedAtMillis,
                startDistanceM = 0.0, endDistanceM = cumulative, avgSpeedMps = null, avgCadenceSpm = null,
            )
        )
        return RunAnalysis(
            stats = stats,
            suggestedType = type,
            suggestedName = namer.name(input.startedAtMillis, input.zoneId, type),
            filteredPoints = filtered,
            splits = splits(filtered, input.startedAtMillis),
            segments = segments,
            bestEfforts = emptyList(),
            learnedRunStrideM = null,
            learnedWalkStrideM = null,
            analysisVersion = 0,
        )
    }

    /** 1 km splits by linear interpolation of time over cumulative distance (elapsed = moving here). */
    private fun splits(points: List<TrackPoint>, startMillis: Long): List<Split> {
        if (points.size < 2) return emptyList()
        val out = mutableListOf<Split>()
        var splitStartTime = startMillis
        var splitStartDist = 0.0
        var nextKm = METERS_PER_KM
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val da = a.distanceM ?: 0.0
            val db = b.distanceM ?: 0.0
            while (db >= nextKm && db > da) {
                val f = (nextKm - da) / (db - da)
                val t = a.timeMillis + ((b.timeMillis - a.timeMillis) * f).toLong()
                out += split(out.size, nextKm - splitStartDist, splitStartTime, t)
                splitStartTime = t
                splitStartDist = nextKm
                nextKm += METERS_PER_KM
            }
        }
        val total = points.last().distanceM ?: 0.0
        if (total - splitStartDist >= 10.0) {
            out += split(out.size, total - splitStartDist, splitStartTime, points.last().timeMillis)
        }
        return out
    }

    private fun split(index: Int, distance: Double, start: Long, end: Long): Split {
        val duration = (end - start).coerceAtLeast(0)
        return Split(
            index = index, distanceM = distance, startMillis = start, endMillis = end,
            movingMillis = duration, elapsedMillis = duration, paceSecPerKm = paceSecPerKm(distance, duration),
            elevationGainM = null, elevationLossM = null, avgCadenceSpm = null, walkFraction = null,
        )
    }
}

internal class StubLiveMetricsFactory : LiveMetricsFactory {
    override fun create(profile: RunnerProfile, startedAtMillis: Long): LiveMetrics = StubLiveMetrics(startedAtMillis)
}

private class StubLiveMetrics(private val startedAtMillis: Long) : LiveMetrics {
    private val route = mutableListOf<RoutePoint>()
    private var distance = 0.0
    private var last: LocationFix? = null
    private var pausedSince: Long? = null
    private var pausedTotal = 0L

    override fun onFix(fix: LocationFix, paused: Boolean) {
        val prev = last
        last = fix
        if (paused) return
        if (prev != null) distance += haversineMeters(prev.lat, prev.lon, fix.lat, fix.lon)
        route += RoutePoint(fix.lat, fix.lon, distance, null, stepFilled = false, segmentKind = null)
    }

    override fun onSteps(event: StepEvent, paused: Boolean) = Unit
    override fun onPressure(sample: PressureSample) = Unit

    override fun onPauseChanged(paused: Boolean, atMillis: Long) {
        if (paused) {
            if (pausedSince == null) pausedSince = atMillis
        } else {
            pausedSince?.let { pausedTotal += atMillis - it }
            pausedSince = null
        }
    }

    override fun snapshot(nowMillis: Long): LiveStats {
        val elapsed = (nowMillis - startedAtMillis).coerceAtLeast(0)
        val paused = pausedTotal + (pausedSince?.let { nowMillis - it } ?: 0)
        val moving = (elapsed - paused).coerceAtLeast(0)
        return LiveStats.ZERO.copy(
            elapsedMillis = elapsed,
            movingMillis = moving,
            distanceM = distance,
            avgPaceSecPerKm = paceSecPerKm(distance, moving),
            currentSplitIndex = (distance / METERS_PER_KM).toInt(),
        )
    }

    override fun acceptedRoute(): MapRoute = MapRoute(route.toList())
}

internal class StubRunSeriesBuilder : RunSeriesBuilder {
    override fun build(filteredPoints: List<TrackPoint>): RunSeries = RunSeries(
        pace = filteredPoints.mapNotNull { p ->
            val d = p.distanceM ?: return@mapNotNull null
            p.paceSecPerKm?.let { SeriesPoint(d / METERS_PER_KM, it) }
        },
        elevation = filteredPoints.mapNotNull { p ->
            val d = p.distanceM ?: return@mapNotNull null
            p.elevationM?.let { SeriesPoint(d / METERS_PER_KM, it) }
        },
        cadence = emptyList(),
    )
}
