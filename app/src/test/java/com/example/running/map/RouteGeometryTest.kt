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
}
