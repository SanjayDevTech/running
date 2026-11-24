package com.example.running.core.model

data class LocationFix(
    val timeMillis: Long,
    val elapsedRealtimeNanos: Long,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val mslAltitudeM: Double?,
    val horizontalAccuracyM: Float?,
    val verticalAccuracyM: Float?,
    val speedMps: Float?,
    val speedAccuracyMps: Float?,
    val bearingDeg: Float?,
    val engine: LocationEngine,
) {
    fun toRawPoint(runId: String, isPrimary: Boolean, paused: Boolean): TrackPoint = TrackPoint(
        runId = runId,
        kind = PointKind.RAW,
        engine = engine,
        isPrimary = isPrimary,
        timeMillis = timeMillis,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        lat = lat,
        lon = lon,
        altitudeM = altitudeM,
        mslAltitudeM = mslAltitudeM,
        horizontalAccuracyM = horizontalAccuracyM,
        verticalAccuracyM = verticalAccuracyM,
        speedMps = speedMps,
        speedAccuracyMps = speedAccuracyMps,
        bearingDeg = bearingDeg,
        paused = paused,
    )
}

/** STEP_DETECTOR (count = 1) or a delta from STEP_COUNTER. */
data class StepEvent(val timeMillis: Long, val count: Int = 1)

data class PressureSample(val timeMillis: Long, val hPa: Double)
