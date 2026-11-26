package com.example.running.map

import androidx.compose.ui.graphics.toArgb
import com.example.running.core.model.GeoPoint
import com.example.running.core.model.MapRoute
import com.example.running.core.model.RouteColoring
import com.example.running.core.model.RoutePoint
import com.example.running.core.model.SegmentKind
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.PaceScale
import com.example.running.ui.theme.StepFilled
import com.example.running.ui.theme.Walk
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class RouteGeometryTest {
    private fun pt(i: Int, pace: Double? = 300.0, step: Boolean = false, kind: SegmentKind? = null) =
        RoutePoint(lat = 12.0 + i * 0.0001, lon = 77.0, distanceM = i * 11.1, paceSecPerKm = pace, stepFilled = step, segmentKind = kind)

    private fun route(vararg pts: RoutePoint, coloring: RouteColoring = RouteColoring.PACE, tint: Int? = null) =
        MapRoute(pts.toList(), coloring, tint)

    @Test
    fun paceNormalizerUsesPercentilesAndClamps() {
        val paces = (1..100).map { 300.0 + it } // 301..400
        val n = PaceNormalizer.of(paces)
        assertThat(n.normalize(null)).isNull()
        assertThat(n.normalize(100.0)).isEqualTo(0.0)
        assertThat(n.normalize(900.0)).isEqualTo(1.0)
        assertThat(n.normalize(350.5)!!).isWithin(0.02).of(0.5)
    }

    @Test
    fun paceNormalizerFlatPaceIsMidScale() {
        assertThat(PaceNormalizer.of(listOf(300.0, 300.0)).normalize(300.0)).isEqualTo(0.5)
        assertThat(PaceNormalizer.of(emptyList()).normalize(300.0)).isEqualTo(0.5)
    }

    @Test
    fun steadyRunStaysMidScale() {
        // 5:40-5:50 /km: the 10 s spread is widened to median ± 30 s, so nothing hits the ends.
        val paces = (0..100).map { 340.0 + it * 0.1 }
        val n = PaceNormalizer.of(paces)
        val ts = paces.map { n.normalize(it)!! }
        assertThat(ts.min()).isAtLeast(0.3)
        assertThat(ts.max()).isAtMost(0.7)
        assertThat(n.normalize(345.0)!!).isWithin(1e-9).of(0.5)
        // Median ± MIN_HALF_SPREAD maps to the ends.
        assertThat(n.normalize(345.0 - PaceNormalizer.MIN_HALF_SPREAD_SEC_PER_KM)!!).isWithin(1e-9).of(0.0)
        assertThat(n.normalize(345.0 + PaceNormalizer.MIN_HALF_SPREAD_SEC_PER_KM)!!).isWithin(1e-9).of(1.0)
    }

    @Test
    fun surgesAndWalkBreaksKeepFullContrast() {
        // Mostly 5:00 /km with 4:00 surges and 9:00 walk breaks: p5/p95 already exceed median ± 30 s.
        val paces = List(80) { 300.0 } + List(10) { 240.0 } + List(10) { 540.0 }
        val n = PaceNormalizer.of(paces)
        assertThat(n.normalize(240.0)).isEqualTo(0.0)
        assertThat(n.normalize(540.0)).isEqualTo(1.0)
    }

    @Test
    fun oneSidedSpreadIsOnlyWidenedOnTheNarrowSide() {
        // Steady 5:00 with a slow tail: hi stays at p95, lo is pulled down to median − 30 s.
        val paces = List(90) { 300.0 } + List(10) { 480.0 }
        val n = PaceNormalizer.of(paces)
        assertThat(n.normalize(270.0)).isEqualTo(0.0)
        assertThat(n.normalize(480.0)).isEqualTo(1.0)
        assertThat(n.normalize(300.0)!!).isWithin(1e-9).of(30.0 / 210.0)
    }

    @Test
    fun paceColorEndpointsMatchTheScale() {
        assertThat(RouteColors.paceArgb(0.0)).isEqualTo(PaceScale.first().toArgb())
        assertThat(RouteColors.paceArgb(1.0)).isEqualTo(PaceScale.last().toArgb())
    }

    @Test
    fun quantizeSnapsToBuckets() {
        assertThat(RouteColors.quantize(0.0)).isEqualTo(0.0)
        assertThat(RouteColors.quantize(1.0)).isEqualTo(1.0)
        assertThat(RouteColors.quantize(0.51)).isWithin(1e-9).of(8 / 15.0)
    }

    @Test
    fun fastAndSlowHalvesGetDifferentColoursAndShareTheJoint() {
        val pts = (0..9).map { pt(it, pace = if (it < 5) 240.0 else 420.0) }.toTypedArray()
        val lines = RouteSegments.build(route(*pts))
        assertThat(lines.size).isAtLeast(2)
        assertThat(lines.first().argb).isNotEqualTo(lines.last().argb)
        // Consecutive lines share their joint so there is no visible gap.
        lines.zipWithNext().forEach { (a, b) -> assertThat(a.coords.last()).isEqualTo(b.coords.first()) }
        assertThat(lines.sumOf { it.coords.size - 1 }).isEqualTo(9)
    }

    @Test
    fun stepFilledSegmentsAreDashedGrey() {
        val pts = arrayOf(pt(0), pt(1), pt(2, step = true), pt(3, step = true), pt(4))
        val lines = RouteSegments.build(route(*pts))
        val dashed = lines.filter { it.stepFilled }
        assertThat(dashed).hasSize(1)
        assertThat(dashed.single().argb).isEqualTo(StepFilled.toArgb())
        assertThat(dashed.single().coords).hasSize(3)
    }

    @Test
    fun solidUsesTintOrAccent() {
        val pts = arrayOf(pt(0), pt(1), pt(2))
        assertThat(RouteSegments.build(route(*pts, coloring = RouteColoring.SOLID, tint = 0xFF112233.toInt())).single().argb)
            .isEqualTo(0xFF112233.toInt())
        assertThat(RouteSegments.build(route(*pts, coloring = RouteColoring.SOLID)).single().argb).isEqualTo(Accent.toArgb())
    }

    @Test
    fun segmentKindColouring() {
        val pts = arrayOf(pt(0), pt(1, kind = SegmentKind.RUN), pt(2, kind = SegmentKind.WALK), pt(3, kind = SegmentKind.WALK))
        val lines = RouteSegments.build(route(*pts, coloring = RouteColoring.SEGMENT_KIND))
        assertThat(lines.map { it.argb }).containsExactly(Accent.toArgb(), Walk.toArgb()).inOrder()
    }

    @Test
    fun missingPaceCarriesPreviousColour() {
        val pts = arrayOf(pt(0, 240.0), pt(1, 240.0), pt(2, null), pt(3, 420.0))
        val lines = RouteSegments.build(route(*pts))
        assertThat(lines[0].coords).hasSize(3) // the null-pace segment joined the first colour
    }

    @Test
    fun tooFewPointsGivesNoLines() {
        assertThat(RouteSegments.build(route(pt(0)))).isEmpty()
        assertThat(RouteSegments.build(MapRoute(emptyList()))).isEmpty()
    }

    @Test
    fun truncateInterpolatesTheCutPoint() {
        val pts = (0..4).map { pt(it) } // distances 0, 11.1, 22.2, ...
        val cut = RouteSegments.truncateToDistance(pts, 16.65)
        assertThat(cut).hasSize(3)
        assertThat(cut.last().distanceM).isEqualTo(16.65)
        assertThat(cut.last().lat).isWithin(1e-9).of(12.00015)
        assertThat(RouteSegments.truncateToDistance(pts, 1e9)).isEqualTo(pts)
        assertThat(RouteSegments.truncateToDistance(pts, 0.0)).hasSize(1)
    }

    @Test
    fun sliceWidensByOneNeighbour() {
        val pts = (0..9).map { pt(it) }
        val s = RouteSegments.slice(pts, 33.3..55.5) // points 3..5
        assertThat(s.first()).isEqualTo(pts[2])
        assertThat(s.last()).isEqualTo(pts[6])
        assertThat(RouteSegments.slice(pts, 500.0..600.0)).isEmpty()
    }

    @Test
    fun decimateKeepsEndsAndBound() {
        val xs = (0..999).toList()
        val d = RouteSegments.decimate(xs, 100)
        assertThat(d).hasSize(100)
        assertThat(d.first()).isEqualTo(0)
        assertThat(d.last()).isEqualTo(999)
        assertThat(RouteSegments.decimate(xs, 5000)).isSameInstanceAs(xs)
    }

    @Test
    fun projectorFitsInsidePaddedRect() {
        val p = RouteProjector(12.0, 77.0, 12.01, 77.02, width = 200f, height = 100f, padding = 10f)
        val xs = listOf(p.x(77.0), p.x(77.02))
        val ys = listOf(p.y(12.0), p.y(12.01))
        assertThat(xs.min()).isAtLeast(10f - 0.01f)
        assertThat(xs.max()).isAtMost(190f + 0.01f)
        assertThat(ys.min()).isAtLeast(10f - 0.01f)
        assertThat(ys.max()).isAtMost(90f + 0.01f)
        assertThat(p.y(12.01)).isLessThan(p.y(12.0)) // north is up
    }

    @Test
    fun geoJsonIsValidAndCarriesProperties() {
        val lines = listOf(
            RouteLine(listOf(GeoPoint(12.0, 77.0), GeoPoint(12.001, 77.002)), 0xFF3DDC97.toInt(), false),
            RouteLine(listOf(GeoPoint(12.001, 77.002), GeoPoint(12.002, 77.003)), 0xFF9A9AA6.toInt(), true),
        )
        val root = Json.parseToJsonElement(RouteGeoJson.lines(lines)).jsonObject
        val features = root.getValue("features").jsonArray
        assertThat(features).hasSize(2)
        val props = features[1].jsonObject.getValue("properties").jsonObject
        assertThat(props.getValue("c").jsonPrimitive.content).isEqualTo("#9a9aa6")
        assertThat(props.getValue("sf").jsonPrimitive.content).isEqualTo("true")
        val first = features[0].jsonObject.getValue("geometry").jsonObject.getValue("coordinates").jsonArray[0].jsonArray
        assertThat(first[0].jsonPrimitive.content.toDouble()).isEqualTo(77.0) // lon first
        assertThat(Json.parseToJsonElement(RouteGeoJson.lines(emptyList())).jsonObject.getValue("features").jsonArray).isEmpty()
    }

    @Test
    fun pointGeoJsonIsValid() {
        val root = Json.parseToJsonElement(RouteGeoJson.points(listOf(GeoPoint(1.5, 2.5) to 0xFFFF5A5F.toInt()))).jsonObject
        val g = root.getValue("features").jsonArray[0].jsonObject.getValue("geometry").jsonObject
        assertThat(g.getValue("type").jsonPrimitive.content).isEqualTo("Point")
    }

    /** A varied route: pace surges, missing paces, step-filled stretches, segment kinds, a repeated distance. */
    private fun variedRoute(n: Int, coloring: RouteColoring): MapRoute {
        val rnd = java.util.Random(7)
        var d = 0.0
        val kinds = listOf(SegmentKind.RUN, SegmentKind.WALK, SegmentKind.STOP, null)
        val pts = (0 until n).map { i ->
            if (i != 40) d += rnd.nextDouble() * 6.0 // point 40 repeats the previous distance
            RoutePoint(
                lat = 12.0 + i * 0.00005 + rnd.nextDouble() * 1e-5, lon = 77.0 + rnd.nextDouble() * 1e-4, distanceM = d,
                paceSecPerKm = if (i % 17 == 0) null else 240.0 + rnd.nextInt(300),
                stepFilled = (i / 25) % 4 == 3,
                segmentKind = kinds[(i / 30) % kinds.size],
            )
        }
        return MapRoute(pts, coloring, tintArgb = if (coloring == RouteColoring.SOLID) Walk.toArgb() else null)
    }

    /** The pre-range implementation of RouteSegments.build, kept as the reference. */
    private fun legacyBuild(route: MapRoute, points: List<RoutePoint>, normalizer: PaceNormalizer): List<RouteLine> {
        if (points.size < 2) return emptyList()
        val lines = ArrayList<RouteLine>()
        var coords = ArrayList<GeoPoint>()
        var curArgb = 0
        var curStep = false
        var lastT = 0.5
        for (i in 1 until points.size) {
            val a = points[i - 1]
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
            if (coords.isEmpty()) {
                coords.add(GeoPoint(a.lat, a.lon)); curArgb = argb; curStep = step
            } else if (argb != curArgb || step != curStep) {
                lines.add(RouteLine(coords, curArgb, curStep))
                coords = arrayListOf(GeoPoint(a.lat, a.lon))
                curArgb = argb; curStep = step
            }
            coords.add(GeoPoint(b.lat, b.lon))
        }
        if (coords.size >= 2) lines.add(RouteLine(coords, curArgb, curStep))
        return lines
    }

    @Test
    fun rangeBasedBuildMatchesTheLegacyBuild() {
        for (coloring in RouteColoring.entries) {
            val r = variedRoute(500, coloring)
            assertThat(RouteSegments.build(r)).isEqualTo(legacyBuild(r, r.points, PaceNormalizer.of(r)))
        }
    }

    @Test
    fun drawingPrefixMatchesTruncateThenBuild() {
        for (coloring in RouteColoring.entries) {
            val r = variedRoute(500, coloring)
            val maxPoints = 180 // also exercises the thinning
            val drawing = RouteDrawing.of(r, maxPoints)!!
            val points = RouteSegments.decimate(r.points, maxPoints)
            val normalizer = PaceNormalizer.of(r)
            assertThat(drawing.lines).isEqualTo(legacyBuild(r, points, normalizer))
            val total = points.last().distanceM
            val cuts = (0..400).map { total * it / 400.0 } + listOf(-1.0, points[40].distanceM, total + 1.0)
            for (d in cuts) {
                val expected = legacyBuild(r, RouteSegments.truncateToDistance(points, d), normalizer)
                assertThat(drawing.linesUpTo(d)).isEqualTo(expected)
            }
        }
        assertThat(RouteDrawing.of(route(pt(0)), 100)).isNull()
    }

    @Test
    fun pointAtDistanceInterpolatesBetweenNeighbours() {
        val pts = (0..4).map { pt(it) } // 11.1 m apart, lat +0.0001 each
        val p = RouteSegments.pointAtDistance(pts, 16.65)!! // halfway between point 1 and 2
        assertThat(p.lat).isWithin(1e-9).of(12.00015)
        assertThat(p.lon).isWithin(1e-9).of(77.0)
        // Exactly on a point.
        assertThat(RouteSegments.pointAtDistance(pts, 22.2)!!.lat).isWithin(1e-9).of(12.0002)
        // Agrees with the cut point of truncateToDistance (same interpolation).
        val cut = RouteSegments.truncateToDistance(pts, 30.0).last()
        val q = RouteSegments.pointAtDistance(pts, 30.0)!!
        assertThat(q.lat).isWithin(1e-12).of(cut.lat)
        assertThat(q.lon).isWithin(1e-12).of(cut.lon)
    }

    @Test
    fun pointAtDistanceClampsToTheRouteEnds() {
        val pts = (0..4).map { pt(it) }
        assertThat(RouteSegments.pointAtDistance(pts, -5.0)).isEqualTo(GeoPoint(12.0, 77.0))
        assertThat(RouteSegments.pointAtDistance(pts, 0.0)).isEqualTo(GeoPoint(12.0, 77.0))
        assertThat(RouteSegments.pointAtDistance(pts, 1e9)).isEqualTo(GeoPoint(pts.last().lat, 77.0))
        assertThat(RouteSegments.pointAtDistance(pts, 44.4)).isEqualTo(GeoPoint(pts.last().lat, 77.0))
        assertThat(RouteSegments.pointAtDistance(listOf(pt(3)), 10.0)).isEqualTo(GeoPoint(pts[3].lat, 77.0))
        assertThat(RouteSegments.pointAtDistance(emptyList(), 10.0)).isNull()
        assertThat(RouteSegments.pointAtDistance(pts, Double.NaN)).isNull()
    }

    @Test
    fun pointAtDistanceHandlesPausesAndRepeatedDistances() {
        // Standing still (same distance twice), then a pause: the route jumps 100 m east with the
        // distance advancing 50 m (the pause itself is not in the route).
        val a = RoutePoint(12.0, 77.0, 0.0, 300.0, false, null)
        val b = RoutePoint(12.0001, 77.0, 11.1, 300.0, false, null)
        val still = RoutePoint(12.0001, 77.0, 11.1, null, false, SegmentKind.STOP)
        val resumed = RoutePoint(12.0001, 77.001, 61.1, 300.0, false, null)
        val end = RoutePoint(12.0002, 77.001, 72.2, 300.0, false, null)
        val pts = listOf(a, b, still, resumed, end)
        // At the repeated distance: the last point carrying it (no division by a zero span).
        assertThat(RouteSegments.pointAtDistance(pts, 11.1)).isEqualTo(GeoPoint(12.0001, 77.0))
        // Inside the pause: on the straight line between the points either side, never off the route.
        val mid = RouteSegments.pointAtDistance(pts, 36.1)!!
        assertThat(mid.lat).isWithin(1e-9).of(12.0001)
        assertThat(mid.lon).isWithin(1e-9).of(77.0005)
        // Monotonic along a long route: every probe lands between its bracketing points (binary search).
        val r = variedRoute(500, RouteColoring.PACE).points
        val first = r.first().distanceM
        val span = r.last().distanceM - first
        for (i in 0..200) {
            val d = first + span * i / 200.0
            val p = RouteSegments.pointAtDistance(r, d)!!
            val after = r.indexOfFirst { it.distanceM > d }.takeIf { it > 0 } ?: r.lastIndex
            val lo = minOf(r[after - 1].lat, r[after].lat) - 1e-12
            val hi = maxOf(r[after - 1].lat, r[after].lat) + 1e-12
            assertThat(p.lat).isAtLeast(lo)
            assertThat(p.lat).isAtMost(hi)
        }
    }
}
