package com.example.running.analysis

import com.example.running.core.model.GpsQuality

/**
 * Every threshold the analysis uses, in one place. Pure Kotlin.
 *
 * GPS rejection thresholds follow the therunner reference: accuracy > 30 m skipped, speed >= 15 m/s
 * skipped, moves < 0.5 m treated as jitter.
 */
internal object AnalysisTuning {
    /** Bumped whenever the analysis output changes meaningfully. 0 = never analysed (stub). */
    const val ANALYSIS_VERSION = 1

    // ---- GPS pre-filter (therunner) ----
    const val MAX_ACCURACY_M = 30.0
    const val MAX_SPEED_MPS = 15.0
    const val JITTER_M = 0.5
    /** A fix closer than [JITTER_M] is still accepted once this much time has passed (keeps the timeline dense). */
    const val JITTER_MAX_SKIP_MILLIS = 3_000L
    /** After this many consecutive speed rejections the filter accepts again (the anchor was the outlier). */
    const val MAX_CONSECUTIVE_SPEED_REJECTS = 5
    /** Accuracy assumed when a fix reports none. */
    const val DEFAULT_ACCURACY_M = 10.0

    // ---- Weak GPS / step fill ----
    /** Worse than this the stretch's distance comes from steps (when steps exist); drawn dashed. */
    const val WEAK_ACCURACY_M = GpsQuality.FAIR_MAX_ACCURACY_M.toDouble()
    /** Gaps between accepted fixes longer than this are filled from steps. */
    const val GAP_MILLIS = 4_000L
    /** Step-filled points are inserted every this many ms inside a gap. */
    const val FILL_POINT_SPACING_MILLIS = 5_000L
    /** Gaps longer than this restart the Kalman filter. */
    const val KALMAN_RESET_GAP_MILLIS = 30_000L

    // ---- Kalman ----
    /** White-noise acceleration std-dev (m/s²) for a runner. */
    const val KALMAN_ACCEL_STD = 1.5
    /** Android accuracy is a 68 % radius; per-axis sigma is roughly accuracy / 1.5. */
    const val ACCURACY_TO_SIGMA = 1.5
    const val MIN_POSITION_SIGMA_M = 1.5
    /** Doppler bearing is trusted only above this speed. */
    const val DOPPLER_BEARING_MIN_SPEED = 1.0
    /** Below this Doppler speed a zero-velocity measurement is applied. */
    const val DOPPLER_STILL_SPEED = 0.5
    const val MIN_DOPPLER_SIGMA = 0.3
    /** Doppler is used for distance only when its reported accuracy is at least this good. */
    const val DOPPLER_MAX_SPEED_ACCURACY = 1.5
    /** Weight of Doppler-integrated distance vs smoothed-position distance when both are good. */
    const val DOPPLER_DISTANCE_WEIGHT = 0.7

    // ---- Stationary gating / stops ----
    /** Half-width of the cadence window used for gating. */
    const val GATING_CADENCE_HALF_WINDOW_MILLIS = 3_000L
    /** Below this cadence (spm) the runner is not stepping. */
    const val STILL_CADENCE_SPM = 20.0
    /** With steps absent, GPS must still show at least this speed to count as moving. */
    const val GPS_MOVING_WITHOUT_STEPS_MPS = 1.0
    /** Without step data: Doppler below this = stationary. */
    const val STILL_DOPPLER_MPS = 0.5
    /** Half-width of the displacement window used for stationary detection. */
    const val WINDOW_SPEED_HALF_MILLIS = 10_000L
    /** Without step data and Doppler: smoothed displacement speed below this = stationary. */
    const val STILL_WINDOW_SPEED_MPS = 0.4
    /** With good GPS, slower than this counts as stopped even when stepping (jogging on the spot). */
    const val STOP_SPEED_MPS = 0.5
    /** Stops shorter than this are absorbed into moving time. */
    const val MIN_STOP_MILLIS = 4_000L
    /** Moving blips shorter than this between two stops become part of the stop. */
    const val MIN_MOVING_BLIP_MILLIS = 3_000L

    // ---- Gait (walk / run) ----
    const val RUN_CADENCE_SPM = 140.0
    const val WALK_CADENCE_SPM = 125.0
    /** Speed tie-breaker between the cadence thresholds, and the only signal without steps. */
    const val RUN_SPEED_MPS = 2.1
    const val GAIT_HALF_WINDOW_MILLIS = 5_000L
    /** Run/walk segments shorter than this are merged into their neighbour. */
    const val MIN_GAIT_SEGMENT_MILLIS = 20_000L
    /** Walk share (of moving distance) above which the activity is a walk. */
    const val WALK_TYPE_FRACTION = 0.5

    // ---- Pace / speed smoothing ----
    const val PACE_HALF_WINDOW_MILLIS = 7_000L
    const val MAX_SPEED_WINDOW_MILLIS = 5_000L

    // ---- Stride ----
    const val SEED_WALK_STRIDE_PER_HEIGHT = 0.415
    const val SEED_RUN_STRIDE_PER_HEIGHT = 0.63
    const val DEFAULT_HEIGHT_CM = 170
    /** Good-GPS stretches used for stride measurement must be at least this accurate. */
    const val STRIDE_MAX_ACCURACY_M = 15.0
    const val STRIDE_MIN_STEPS = 150.0
    /** Weight of this run's measurement when blending with the stored learned stride. */
    const val STRIDE_LEARN_RATE = 0.3
    /** Local stride for a step fill uses good stretches within this time of the gap. */
    const val LOCAL_STRIDE_WINDOW_MILLIS = 180_000L
    const val LOCAL_STRIDE_MIN_STEPS = 60.0
    val WALK_STRIDE_RANGE = 0.35..1.2
    val RUN_STRIDE_RANGE = 0.5..2.2

    // ---- Elevation ----
    const val BARO_MIN_SAMPLES = 10
    const val BARO_SMOOTH_HALF_WINDOW_MILLIS = 5_000L
    const val BARO_HYSTERESIS_M = 1.0
    const val GPS_ALT_SMOOTH_HALF_WINDOW_MILLIS = 20_000L
    const val GPS_ALT_HYSTERESIS_M = 4.0
    const val GPS_ALT_MAX_VERTICAL_ACCURACY_M = 25.0
    const val GPS_ANCHOR_MAX_VERTICAL_ACCURACY_M = 15.0

    // ---- Live ----
    const val LIVE_PACE_WINDOW_MILLIS = 12_000L
    const val LIVE_CADENCE_WINDOW_MILLIS = 15_000L
    const val LIVE_STILL_NO_STEPS_MILLIS = 4_000L
    /** Live moving time accrues across an event gap only up to this long. */
    const val LIVE_MAX_ACCRUAL_MILLIS = 5_000L
}
