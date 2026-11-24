package com.example.running.core

import com.example.running.core.model.GeoBounds
import com.example.running.core.model.GeoPoint
import com.example.running.core.util.bearingDegrees
import com.example.running.core.util.haversineMeters
import com.example.running.core.util.interpolate
import com.example.running.core.util.paceSecPerKm
import com.example.running.core.util.pathLengthMeters
import com.example.running.core.util.speedMps
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoMathTest {
    @Test fun haversineOneDegreeOfLatitude() {
        assertThat(haversineMeters(0.0, 0.0, 1.0, 0.0)).isWithin(1.0).of(111_195.08)
        assertThat(haversineMeters(GeoPoint(12.0, 77.0), GeoPoint(12.0, 77.0))).isEqualTo(0.0)
    }

    @Test fun bearings() {
        assertThat(bearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))).isWithin(1e-6).of(0.0)
        assertThat(bearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))).isWithin(1e-6).of(90.0)
        assertThat(bearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(-1.0, 0.0))).isWithin(1e-6).of(180.0)
        assertThat(bearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(0.0, -1.0))).isWithin(1e-6).of(270.0)
    }

    @Test fun interpolateAndPath() {
        val a = GeoPoint(10.0, 20.0)
        val b = GeoPoint(10.002, 20.0)
        val mid = interpolate(a, b, 0.5)
        assertThat(mid.lat).isWithin(1e-9).of(10.001)
        assertThat(mid.lon).isWithin(1e-9).of(20.0)
        assertThat(pathLengthMeters(listOf(a, mid, b))).isWithin(1e-6).of(haversineMeters(a, b))
        assertThat(pathLengthMeters(listOf(a))).isEqualTo(0.0)
    }

    @Test fun bounds() {
        val b = GeoBounds.of(listOf(GeoPoint(1.0, 2.0), GeoPoint(-1.0, 5.0), GeoPoint(0.5, 3.0)))!!
        assertThat(b).isEqualTo(GeoBounds(-1.0, 2.0, 1.0, 5.0))
        assertThat(b.center).isEqualTo(GeoPoint(0.0, 3.5))
        assertThat(GeoBounds.of(emptyList())).isNull()
        val p = b.padded(1_000.0)
        assertThat(p.minLat).isLessThan(b.minLat)
        assertThat(p.maxLon).isGreaterThan(b.maxLon)
        assertThat(haversineMeters(b.maxLat, 3.5, p.maxLat, 3.5)).isWithin(5.0).of(1_000.0)
    }

    @Test fun units() {
        assertThat(paceSecPerKm(3.0)).isWithin(1e-9).of(1000.0 / 3.0)
        assertThat(paceSecPerKm(0.2)).isNull()
        assertThat(paceSecPerKm(1_000.0, 300_000L)).isWithin(1e-9).of(300.0)
        assertThat(paceSecPerKm(5.0, 300_000L)).isNull()
        assertThat(speedMps(300.0)).isWithin(1e-9).of(1000.0 / 300.0)
    }
}
