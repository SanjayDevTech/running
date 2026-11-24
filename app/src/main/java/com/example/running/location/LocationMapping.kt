package com.example.running.location

import android.location.Location
import android.os.Build
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.model.TrackPoint

/** Engine-neutral copy of an Android [Location]; absent optional fields become null. */
internal fun Location.toFix(engine: LocationEngine): LocationFix = LocationFix(
    timeMillis = time,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    lat = latitude,
    lon = longitude,
    altitudeM = if (hasAltitude()) altitude else null,
    mslAltitudeM = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude()) mslAltitudeMeters else null,
    horizontalAccuracyM = if (hasAccuracy()) accuracy else null,
    verticalAccuracyM = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
    speedMps = if (hasSpeed()) speed else null,
    speedAccuracyMps = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    bearingDeg = if (hasBearing()) bearing else null,
    engine = engine,
)

/** A stored RAW point back as the fix it came from (used to replay a run after a process restart). */
internal fun TrackPoint.toFix(): LocationFix = LocationFix(
    timeMillis = timeMillis,
    elapsedRealtimeNanos = elapsedRealtimeNanos ?: 0L,
    lat = lat,
    lon = lon,
    altitudeM = altitudeM,
    mslAltitudeM = mslAltitudeM,
    horizontalAccuracyM = horizontalAccuracyM,
    verticalAccuracyM = verticalAccuracyM,
    speedMps = speedMps,
    speedAccuracyMps = speedAccuracyMps,
    bearingDeg = bearingDeg,
    engine = engine ?: LocationEngine.PLAY,
)
