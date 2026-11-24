package com.example.running.core.model

/** Mirrors TrackPointEntity without the row id. */
data class TrackPoint(
    val runId: String,
    val kind: PointKind,
    val engine: LocationEngine?,
    val isPrimary: Boolean,
    val timeMillis: Long,
    val elapsedRealtimeNanos: Long?,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val mslAltitudeM: Double?,
    val horizontalAccuracyM: Float?,
    val verticalAccuracyM: Float?,
    val speedMps: Float?,
    val speedAccuracyMps: Float?,
    val bearingDeg: Float?,
    val paused: Boolean,
    val stepFilled: Boolean = false,
    val distanceM: Double? = null,
    val elevationM: Double? = null,
    val paceSecPerKm: Double? = null,
    val segmentKind: SegmentKind? = null,
)
