package com.example.running.core.model

import com.example.running.core.util.METERS_PER_DEGREE_LAT
import com.example.running.core.util.metersPerDegreeLon

data class GeoPoint(val lat: Double, val lon: Double)

data class GeoBounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    val center: GeoPoint get() = GeoPoint((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)

    /** Grows the box by [meters] on every side (approximate, fine away from the poles). */
    fun padded(meters: Double): GeoBounds {
        val dLat = meters / METERS_PER_DEGREE_LAT
        val dLon = meters / metersPerDegreeLon(center.lat)
        return GeoBounds(
            minLat = (minLat - dLat).coerceAtLeast(-90.0),
            minLon = (minLon - dLon).coerceAtLeast(-180.0),
            maxLat = (maxLat + dLat).coerceAtMost(90.0),
            maxLon = (maxLon + dLon).coerceAtMost(180.0),
        )
    }

    companion object {
        /** Bounds from four nullable columns (DB row, backup payload); null unless all are set. */
        fun ofNullable(minLat: Double?, minLon: Double?, maxLat: Double?, maxLon: Double?): GeoBounds? =
            if (minLat != null && minLon != null && maxLat != null && maxLon != null) {
                GeoBounds(minLat, minLon, maxLat, maxLon)
            } else {
                null
            }

        fun of(points: Iterable<GeoPoint>): GeoBounds? {
            var minLat = Double.POSITIVE_INFINITY
            var minLon = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            var any = false
            for (p in points) {
                any = true
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lon < minLon) minLon = p.lon
                if (p.lon > maxLon) maxLon = p.lon
            }
            return if (any) GeoBounds(minLat, minLon, maxLat, maxLon) else null
        }
    }
}
