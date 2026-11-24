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

/**
 * STEP_DETECTOR: one per step (value 1). STEP_COUNTER: this run's own cumulative count from step-counter
 * deltas (counter-only devices). PRESSURE: hPa.
 * STEP_COUNTER_BOOT: raw TYPE_STEP_COUNTER reading (cumulative since boot), stored at most once per flush;
 * it keeps counting while the app is dead, so it bridges a process-death gap (§3.1).
 * RESUME_GAP: one per resume after an interruption, at the resume time; value = steps taken during the
 * gap (from STEP_COUNTER_BOOT), or -1 when unknown (no counter, reboot, counter reset).
 */
enum class SensorType { STEP_DETECTOR, STEP_COUNTER, PRESSURE, STEP_COUNTER_BOOT, RESUME_GAP }

enum class ElevationSource { BAROMETER, GPS, NONE }

enum class Sex { MALE, FEMALE, UNSPECIFIED }

/** NONE: no fix; POOR > 20 m; FAIR 10–20 m; GOOD <= 10 m. */
enum class GpsQuality {
    NONE, POOR, FAIR, GOOD;

    companion object {
        /** Best accuracy band: at or below this a fix is GOOD (and unlocks Start at once). */
        const val GOOD_MAX_ACCURACY_M = 10f

        /** Worse than this a fix is POOR ("weak GPS"; the analysis step-fills such stretches). */
        const val FAIR_MAX_ACCURACY_M = 20f

        fun fromAccuracy(accuracyM: Float?): GpsQuality = when {
            accuracyM == null -> NONE
            accuracyM <= GOOD_MAX_ACCURACY_M -> GOOD
            accuracyM <= FAIR_MAX_ACCURACY_M -> FAIR
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
