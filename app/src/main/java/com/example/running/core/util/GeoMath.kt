package com.example.running.core.util

import com.example.running.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Pure Kotlin: usable from analysis/ (track B).

const val EARTH_RADIUS_M = 6_371_008.8

/** Metres per degree of latitude on the mean-radius sphere (same sphere as [haversineMeters]). */
const val METERS_PER_DEGREE_LAT = EARTH_RADIUS_M * PI / 180.0

/**
 * Shrink factor of a degree of longitude at [lat] for equirectangular projections, floored so it
 * never reaches zero at the poles.
 */
fun cosLatFactor(lat: Double): Double = cos(Math.toRadians(lat)).coerceAtLeast(0.01)

/** Metres per degree of longitude at [lat]. */
fun metersPerDegreeLon(lat: Double): Double = METERS_PER_DEGREE_LAT * cosLatFactor(lat)

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dPhi = Math.toRadians(lat2 - lat1)
    val dLambda = Math.toRadians(lon2 - lon1)
    val a = sin(dPhi / 2).let { it * it } + cos(phi1) * cos(phi2) * sin(dLambda / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
}

fun haversineMeters(a: GeoPoint, b: GeoPoint): Double = haversineMeters(a.lat, a.lon, b.lat, b.lon)

/** Initial bearing from [a] to [b], 0..360 degrees clockwise from north. */
fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
    val phi1 = Math.toRadians(a.lat)
    val phi2 = Math.toRadians(b.lat)
    val dLambda = Math.toRadians(b.lon - a.lon)
    val y = sin(dLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Linear interpolation in lat/lon (fine for the short hops between fixes). */
fun interpolate(a: GeoPoint, b: GeoPoint, fraction: Double): GeoPoint =
    GeoPoint(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction)

fun pathLengthMeters(points: List<GeoPoint>): Double {
    var total = 0.0
    for (i in 1 until points.size) total += haversineMeters(points[i - 1], points[i])
    return total
}
