package com.example.running.analysis

import com.example.running.core.contracts.RunNamer
import com.example.running.core.contracts.RunSeriesBuilder
import com.example.running.core.model.ActivityType
import com.example.running.core.model.RunSeries
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.SensorSample
import com.example.running.core.model.SeriesPoint
import com.example.running.core.model.TrackPoint
import com.example.running.core.util.METERS_PER_KM
import java.time.Instant
import java.time.ZoneId

/** "Morning Run", "Lunch Walk", … from the local start hour (5–11 Morning, 11–14 Lunch, 14–17 Afternoon, 17–21 Evening, else Night). */
internal class DefaultRunNamer : RunNamer {
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

/**
 * Chart series over distance. Pace excludes stops and is smoothed and downsampled to at most
 * [maxPoints] distance buckets; elevation likewise. Cadence needs the run's step samples, which the
 * run's step samples ([sensorSamples]); without them it is empty.
 */
internal class DefaultRunSeriesBuilder(private val maxPoints: Int = 300) : RunSeriesBuilder {

    override fun build(filteredPoints: List<TrackPoint>, sensorSamples: List<SensorSample>): RunSeries {
        val pts = filteredPoints.filter { it.distanceM != null && !it.paused }.sortedBy { it.timeMillis }
        val pace = bucket(pts.filter { it.segmentKind != SegmentKind.STOP && it.paceSecPerKm != null }) { it.paceSecPerKm!! }
        val elevation = bucket(pts.filter { it.elevationM != null }) { it.elevationM!! }
        return RunSeries(pace = smooth(pace), elevation = elevation, cadence = cadence(pts, sensorSamples))
    }

    private fun bucket(points: List<TrackPoint>, value: (TrackPoint) -> Double): List<SeriesPoint> {
        if (points.isEmpty()) return emptyList()
        val total = points.last().distanceM!!
        if (points.size <= maxPoints || total <= 0) return points.map { SeriesPoint(it.distanceM!! / METERS_PER_KM, value(it)) }
        val width = total / maxPoints
        val out = mutableListOf<SeriesPoint>()
        var i = 0
        while (i < points.size) {
            val b = (points[i].distanceM!! / width).toInt()
            var sumD = 0.0
            var sumV = 0.0
            var n = 0
            while (i < points.size && (points[i].distanceM!! / width).toInt() == b) {
                sumD += points[i].distanceM!!; sumV += value(points[i]); n++; i++
            }
            out += SeriesPoint(sumD / n / METERS_PER_KM, sumV / n)
        }
        return out
    }

    /** 3-point median then mean, enough to calm GPS pace spikes without hiding intervals. */
    private fun smooth(series: List<SeriesPoint>): List<SeriesPoint> {
        if (series.size < 3) return series
        val median = series.mapIndexed { i, p ->
            if (i == 0 || i == series.lastIndex) p
            else p.copy(value = listOf(series[i - 1].value, p.value, series[i + 1].value).sorted()[1])
        }
        return median.mapIndexed { i, p ->
            if (i == 0 || i == median.lastIndex) p
            else p.copy(value = (median[i - 1].value + p.value + median[i + 1].value) / 3)
        }
    }

    private fun cadence(points: List<TrackPoint>, samples: List<SensorSample>): List<SeriesPoint> {
        val steps = StepTimeline.from(samples)
        if (!steps.hasData) return emptyList()
        val moving = points.filter { it.segmentKind != SegmentKind.STOP }
        return bucket(moving) { steps.cadenceAround(it.timeMillis, AnalysisTuning.GAIT_HALF_WINDOW_MILLIS) }
    }
}
