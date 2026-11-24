package com.example.running.core.model

// Stored in Room by name: renaming a constant is a schema change.

enum class ActivityType { RUN, WALK }

enum class ActivityFilter {
    ALL, RUN, WALK;

    val type: ActivityType?
        get() = when (this) {
            ALL -> null
            RUN -> ActivityType.RUN
            WALK -> ActivityType.WALK
        }
}

enum class RunStatus { ACTIVE, FINISHED }

/** User setting; labels "Auto", "Google", "Built-in". */
enum class LocationEngineMode { AUTO, GOOGLE, BUILT_IN }

/** What actually produced a fix. */
enum class LocationEngine { PLAY, PLATFORM }

enum class PointKind { RAW, FILTERED }

enum class SegmentKind { RUN, WALK, STOP }

enum class SensorType { STEP_DETECTOR, STEP_COUNTER, PRESSURE }

enum class ElevationSource { BAROMETER, GPS, NONE }

enum class Sex { MALE, FEMALE, UNSPECIFIED }

/** NONE: no fix; POOR > 20 m; FAIR 10–20 m; GOOD <= 10 m. */
enum class GpsQuality {
    NONE, POOR, FAIR, GOOD;

    companion object {
        fun fromAccuracy(accuracyM: Float?): GpsQuality = when {
            accuracyM == null -> NONE
            accuracyM <= 10f -> GOOD
            accuracyM <= 20f -> FAIR
            else -> POOR
        }
    }
}

enum class BestEffortDistance(val meters: Double, val label: String) {
    K1(1_000.0, "1K"),
    K5(5_000.0, "5K"),
    K10(10_000.0, "10K"),
    HALF(21_097.5, "Half marathon"),
}
