package com.example.running.map

import com.example.running.core.model.GeoPoint
import kotlin.math.round

/** Hand-rolled GeoJSON strings (no allocation of SDK geometry objects, testable without Android). */
internal object RouteGeoJson {
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /** Features carry `c` (colour as #RRGGBB) and `sf` (step-filled). */
    fun lines(lines: List<RouteLine>): String {
        if (lines.isEmpty()) return EMPTY
        val sb = StringBuilder(64 + lines.sumOf { it.coords.size * 24 })
        sb.append("""{"type":"FeatureCollection","features":[""")
        lines.forEachIndexed { i, line ->
            if (i > 0) sb.append(',')
            sb.append("""{"type":"Feature","properties":{"c":"""").append(RouteColors.hex(line.argb))
                .append("""","sf":""").append(line.stepFilled)
                .append("""},"geometry":{"type":"LineString","coordinates":[""")
            line.coords.forEachIndexed { j, p ->
                if (j > 0) sb.append(',')
                appendCoord(sb, p)
            }
            sb.append("]}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    /** One tinted solid line per route (comparison overlays). */
    fun tintedLines(routes: List<Pair<List<GeoPoint>, Int>>): String =
        lines(routes.filter { it.first.size >= 2 }.map { (coords, argb) -> RouteLine(coords, argb, false) })

    /** Points with colour `c`, e.g. start / end markers or the user dot. */
    fun points(points: List<Pair<GeoPoint, Int>>): String {
        if (points.isEmpty()) return EMPTY
        val sb = StringBuilder()
        sb.append("""{"type":"FeatureCollection","features":[""")
        points.forEachIndexed { i, (p, argb) ->
            if (i > 0) sb.append(',')
            sb.append("""{"type":"Feature","properties":{"c":"""").append(RouteColors.hex(argb))
                .append("\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":")
            appendCoord(sb, p)
            sb.append("}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun appendCoord(sb: StringBuilder, p: GeoPoint) {
        sb.append('[').append(round6(p.lon)).append(',').append(round6(p.lat)).append(']')
    }

    private fun round6(v: Double): Double = round(v * 1e6) / 1e6
}
