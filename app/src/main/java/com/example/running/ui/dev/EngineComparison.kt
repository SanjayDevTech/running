package com.example.running.ui.dev

import com.example.running.core.model.GeoPoint
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.TrackPoint
import com.example.running.core.util.pathLengthMeters

/** Per-engine numbers for the hidden dual-engine comparison. */
internal data class EngineStats(
    val engine: LocationEngine,
    val fixes: Int,
    /** Raw path length over non-paused stretches (no filtering: the point is to compare engines). */
    val distanceM: Double,
    val medianAccuracyM: Double?,
    val p95AccuracyM: Double?,
    val meanIntervalMillis: Double?,
    val maxGapMillis: Long?,
    /** Share of this engine's fixes that drove the run. */
    val primaryShare: Double,
)

internal fun compareEngines(rawPoints: List<TrackPoint>): List<EngineStats> =
    rawPoints.filter { it.engine != null }
        .groupBy { it.engine!! }
        .toSortedMap()
        .map { (engine, points) -> engineStats(engine, points.sortedBy { it.timeMillis }) }

private fun engineStats(engine: LocationEngine, points: List<TrackPoint>): EngineStats {
    val accuracies = points.mapNotNull { it.horizontalAccuracyM?.toDouble() }.sorted()
    val intervals = points.zipWithNext { a, b -> b.timeMillis - a.timeMillis }
    return EngineStats(
        engine = engine,
        fixes = points.size,
        distanceM = movingStretches(points).sumOf { stretch -> pathLengthMeters(stretch.map { GeoPoint(it.lat, it.lon) }) },
        medianAccuracyM = percentile(accuracies, 0.5),
        p95AccuracyM = percentile(accuracies, 0.95),
        meanIntervalMillis = intervals.takeIf { it.isNotEmpty() }?.average(),
        maxGapMillis = intervals.maxOrNull(),
        primaryShare = if (points.isEmpty()) 0.0 else points.count { it.isPrimary }.toDouble() / points.size,
    )
}

/** Consecutive runs of non-paused points (distance across a pause is not counted). */
private fun movingStretches(points: List<TrackPoint>): List<List<TrackPoint>> {
    val out = mutableListOf<List<TrackPoint>>()
    var current = mutableListOf<TrackPoint>()
    for (p in points) {
        if (p.paused) {
            if (current.size > 1) out += current
            current = mutableListOf()
        } else {
            current += p
        }
    }
    if (current.size > 1) out += current
    return out
}

/** Nearest-rank percentile of an ascending list. */
internal fun percentile(sorted: List<Double>, p: Double): Double? {
    if (sorted.isEmpty()) return null
    val rank = kotlin.math.ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}
