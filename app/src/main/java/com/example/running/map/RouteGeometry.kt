package com.example.running.map

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.MapRoute
import com.example.running.core.model.RouteColoring
import com.example.running.core.model.RoutePoint
import com.example.running.core.model.SegmentKind
import com.example.running.core.util.cosLatFactor
import com.example.running.core.util.interpolate
import com.example.running.core.util.percentile
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.PaceScale
import com.example.running.ui.theme.StepFilled
import com.example.running.ui.theme.color
import kotlin.math.max
import kotlin.math.roundToInt

/** One drawable run of consecutive route points that share a colour and a step-filled flag. */
internal data class RouteLine(val coords: List<GeoPoint>, val argb: Int, val stepFilled: Boolean)

/** A [RouteLine] as the index range points[from..to] (inclusive, at least two points). */
internal data class LineRange(val from: Int, val to: Int, val argb: Int, val stepFilled: Boolean) {
    fun toLine(points: List<RoutePoint>): RouteLine =
        RouteLine((from..to).map { GeoPoint(points[it].lat, points[it].lon) }, argb, stepFilled)
}

/**
 * A route prepared once for drawing (off the main thread): thinned to at most `maxPoints`, coloured
 * against the full route's pace range, split into lines. [linesUpTo] gives exactly what
 * `RouteSegments.build(route, RouteSegments.truncateToDistance(points, d), normalizer)` gives, without
 * re-colouring the route on every draw-in frame.
 */
internal class RouteDrawing private constructor(
    val points: List<RoutePoint>,
    private val ranges: List<LineRange>,
) {
    val start: GeoPoint get() = GeoPoint(points.first().lat, points.first().lon)
    val end: GeoPoint get() = GeoPoint(points.last().lat, points.last().lon)
    val totalDistanceM: Double get() = points.last().distanceM

    val lines: List<RouteLine> by lazy { ranges.map { it.toLine(points) } }

    /** The lines up to [distanceM] on the route's distance axis, the cut point interpolated. */
    fun linesUpTo(distanceM: Double): List<RouteLine> {
        if (distanceM >= points.last().distanceM) return lines
        if (distanceM <= points.first().distanceM) return emptyList()
        // First point strictly past the cut (distances never decrease), as truncateToDistance picks it.
        var lo = 1
        var hi = points.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (points[mid].distanceM > distanceM) hi = mid else lo = mid + 1
        }
        val idx = lo
        val a = points[idx - 1]
        val b = points[idx]
        val span = b.distanceM - a.distanceM
        val f = if (span <= 0.0) 1.0 else (distanceM - a.distanceM) / span
        val cut = interpolate(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon), f)
        val out = ArrayList<RouteLine>()
        for (r in ranges) {
            if (r.from >= idx) break
            if (r.to < idx) {
                out += r.toLine(points)
            } else {
                out += RouteLine((r.from until idx).map { GeoPoint(points[it].lat, points[it].lon) } + cut, r.argb, r.stepFilled)
                break
            }
        }
        return out
    }

    companion object {
        /** Null when the route has fewer than two points. */
        fun of(route: MapRoute, maxPoints: Int): RouteDrawing? {
            if (route.points.size < 2) return null
            val points = RouteSegments.decimate(route.points, maxPoints)
            return RouteDrawing(points, RouteSegments.ranges(route, points, PaceNormalizer.of(route)))
        }
    }
}

/**
 * Normalises pace to 0 (fast) .. 1 (slow) against the run's own 5th-95th percentile pace, widened to
 * at least [MIN_HALF_SPREAD_SEC_PER_KM] either side of the median pace (architecture.md §3.4). Runs
 * with real surges or walk breaks keep the full colour scale; a steady run (5:40-5:50 /km) stays
 * mostly mid-scale instead of stretching a 10 s difference from green to red.
 */
internal class PaceNormalizer private constructor(private val lo: Double, private val hi: Double) {
    /** 0..1 or null when [paceSecPerKm] is null. */
    fun normalize(paceSecPerKm: Double?): Double? {
        if (paceSecPerKm == null) return null
        if (hi - lo < MIN_SPREAD_SEC) return 0.5
        return ((paceSecPerKm - lo) / (hi - lo)).coerceIn(0.0, 1.0)
    }

    companion object {
        private const val MIN_SPREAD_SEC = 1.0

        /** Smallest distance (s/km) from the median pace to either end of the colour scale. */
        const val MIN_HALF_SPREAD_SEC_PER_KM = 30.0

        fun of(route: MapRoute): PaceNormalizer = of(route.points.mapNotNull { it.paceSecPerKm })

        fun of(paces: List<Double>): PaceNormalizer {
            val sorted = paces.filter { it.isFinite() && it > 0.0 }.sorted()
            if (sorted.isEmpty()) return PaceNormalizer(0.0, 0.0)
            val median = percentile(sorted, 0.5)!!
            val lo = minOf(percentile(sorted, 0.05)!!, median - MIN_HALF_SPREAD_SEC_PER_KM)
            val hi = maxOf(percentile(sorted, 0.95)!!, median + MIN_HALF_SPREAD_SEC_PER_KM)
            return PaceNormalizer(lo, hi)
        }
    }
}

internal object RouteColors {
    /** Buckets keep the number of distinct colours (and so GeoJSON features) small. */
    const val PACE_BUCKETS = 16

    /** Position 0..1 on [PaceScale] (fast to slow) as ARGB. */
    fun paceArgb(t: Double): Int {
        val scaled = t.coerceIn(0.0, 1.0) * (PaceScale.size - 1)
        val i = scaled.toInt().coerceAtMost(PaceScale.size - 2)
        return lerp(PaceScale[i], PaceScale[i + 1], (scaled - i).toFloat()).toArgb()
    }

    fun quantize(t: Double): Double = (t.coerceIn(0.0, 1.0) * (PACE_BUCKETS - 1)).roundToInt() / (PACE_BUCKETS - 1).toDouble()

    val stepFilledArgb: Int = StepFilled.toArgb()

    fun kindArgb(kind: SegmentKind?): Int = kind.color().toArgb()

    fun hex(argb: Int): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')
}

internal object RouteSegments {
    /**
     * Splits [points] into coloured lines. Pace colour scale comes from [normalizer] (computed from
     * the full route so a draw-in animation does not shift colours). Step-filled segments are always
     * [StepFilled] and flagged so the renderer can dash them.
     */
    fun build(
        route: MapRoute,
        points: List<RoutePoint> = route.points,
        normalizer: PaceNormalizer = PaceNormalizer.of(route),
    ): List<RouteLine> = ranges(route, points, normalizer).map { it.toLine(points) }

    /**
     * The lines of [build] as index ranges into [points]: line k draws points[from..to]. Colours depend
     * only on a point and the ones before it, so the ranges of any prefix of [points] are these ranges
     * cut at the prefix end (what [RouteDrawing] relies on for the draw-in).
     */
    fun ranges(route: MapRoute, points: List<RoutePoint>, normalizer: PaceNormalizer): List<LineRange> {
        if (points.size < 2) return emptyList()
        val out = ArrayList<LineRange>()
        var from = 0
        var curArgb = 0
        var curStep = false
        var lastT = 0.5
        for (i in 1 until points.size) {
            val b = points[i]
            val step = b.stepFilled
            val argb = when {
                step -> RouteColors.stepFilledArgb
                else -> when (route.coloring) {
                    RouteColoring.SOLID -> route.tintArgb ?: Accent.toArgb()
                    RouteColoring.SEGMENT_KIND -> RouteColors.kindArgb(b.segmentKind)
                    RouteColoring.PACE -> {
                        val t = normalizer.normalize(b.paceSecPerKm)
                        if (t != null) lastT = t
                        RouteColors.paceArgb(RouteColors.quantize(lastT))
                    }
                }
            }
            if (i == 1) {
                curArgb = argb; curStep = step
            } else if (argb != curArgb || step != curStep) {
                out.add(LineRange(from, i - 1, curArgb, curStep))
                from = i - 1
                curArgb = argb; curStep = step
            }
        }
        out.add(LineRange(from, points.lastIndex, curArgb, curStep))
        return out
    }

    /** Points up to [distanceM] (route's own distance axis) with the cut point interpolated. */
    fun truncateToDistance(points: List<RoutePoint>, distanceM: Double): List<RoutePoint> {
        if (points.isEmpty() || distanceM >= points.last().distanceM) return points
        if (distanceM <= points.first().distanceM) return points.take(1)
        val idx = points.indexOfFirst { it.distanceM > distanceM }
        val a = points[idx - 1]
        val b = points[idx]
        val span = b.distanceM - a.distanceM
        val f = if (span <= 0.0) 1.0 else (distanceM - a.distanceM) / span
        val mid = interpolate(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon), f)
        return points.subList(0, idx) + b.copy(lat = mid.lat, lon = mid.lon, distanceM = distanceM)
    }

    /** Points whose distance lies in [range], widened by one neighbour each side so the line has no gap. */
    fun slice(points: List<RoutePoint>, range: ClosedFloatingPointRange<Double>): List<RoutePoint> {
        if (points.size < 2) return emptyList()
        val first = points.indexOfFirst { it.distanceM >= range.start }.let { if (it < 0) return emptyList() else max(it - 1, 0) }
        val last = points.indexOfLast { it.distanceM <= range.endInclusive }.let { if (it < 0) return emptyList() else minOf(it + 1, points.lastIndex) }
        return if (last > first) points.subList(first, last + 1) else emptyList()
    }

    /** Evenly strided subset that always keeps first and last point; no-op when already small. */
    fun <T> decimate(points: List<T>, maxPoints: Int): List<T> {
        if (points.size <= maxPoints || maxPoints < 2) return points
        val stride = (points.size - 1).toDouble() / (maxPoints - 1)
        return List(maxPoints) { points[(it * stride).roundToInt().coerceAtMost(points.lastIndex)] }
    }
}

/** Equirectangular fit of a lat/lon box into a pixel rectangle (used by the list thumbnails). */
internal class RouteProjector(
    minLat: Double, minLon: Double, maxLat: Double, maxLon: Double,
    width: Float, height: Float, padding: Float,
) {
    private val minLon = minLon
    private val maxLat = maxLat
    private val scale: Float
    private val offX: Float
    private val offY: Float
    private val cosLat = cosLatFactor((minLat + maxLat) / 2.0)

    init {
        val spanX = max((maxLon - minLon) * cosLat, 1e-9)
        val spanY = max(maxLat - minLat, 1e-9)
        val availW = max(width - 2 * padding, 1f)
        val availH = max(height - 2 * padding, 1f)
        scale = minOf(availW / spanX.toFloat(), availH / spanY.toFloat())
        offX = padding + (availW - spanX.toFloat() * scale) / 2f
        offY = padding + (availH - spanY.toFloat() * scale) / 2f
    }

    fun x(lon: Double): Float = offX + ((lon - minLon) * cosLat).toFloat() * scale
    fun y(lat: Double): Float = offY + (maxLat - lat).toFloat() * scale
}
