package com.example.running.core.model

data class PauseInterval(val startMillis: Long, val endMillis: Long?)

data class Split(
    val index: Int,
    val distanceM: Double,
    val startMillis: Long,
    val endMillis: Long,
    val movingMillis: Long,
    val elapsedMillis: Long,
    val paceSecPerKm: Double?,
    val elevationGainM: Double?,
    val elevationLossM: Double?,
    val avgCadenceSpm: Double?,
    val walkFraction: Double?,
)

data class Segment(
    val kind: SegmentKind,
    val startMillis: Long,
    val endMillis: Long,
    val startDistanceM: Double,
    val endDistanceM: Double,
    val avgSpeedMps: Double?,
    val avgCadenceSpm: Double?,
)

data class BestEffort(
    val distance: BestEffortDistance,
    val durationMillis: Long,
    val startMillis: Long,
    val endMillis: Long,
    val startDistanceM: Double,
)

data class BestEffortWithRank(val effort: BestEffort, val rank: Int) {
    val isPersonalBest: Boolean get() = rank == 1
}

data class SensorSample(val type: SensorType, val timeMillis: Long, val value: Double)
