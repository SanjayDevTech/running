package com.example.running.core.model

data class AnalysisInput(
    val runId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val zoneId: String,
    /** Kind RAW, isPrimary = true, time-ordered. */
    val rawPoints: List<TrackPoint>,
    /** Closed intervals. */
    val pauses: List<PauseInterval>,
    /** Time-ordered. */
    val sensorSamples: List<SensorSample>,
    val profile: RunnerProfile,
    /** Non-null if the user already chose a type. */
    val manualType: ActivityType?,
)

data class RunAnalysis(
    val stats: RunStats,
    val suggestedType: ActivityType,
    val suggestedName: String,
    /** Kind FILTERED with distanceM / elevationM / paceSecPerKm / segmentKind / stepFilled set. */
    val filteredPoints: List<TrackPoint>,
    val splits: List<Split>,
    /** RUN / WALK / STOP covering the moving timeline. */
    val segments: List<Segment>,
    /** Only distances actually covered. */
    val bestEfforts: List<BestEffort>,
    val learnedRunStrideM: Double?,
    val learnedWalkStrideM: Double?,
    val analysisVersion: Int,
)

data class SeriesPoint(val distanceKm: Double, val value: Double)

data class RunSeries(
    /** sec/km, smoothed, stops removed. */
    val pace: List<SeriesPoint>,
    /** Metres. */
    val elevation: List<SeriesPoint>,
    /** spm (empty if no step data). */
    val cadence: List<SeriesPoint>,
    /** Distances (km) where the pace line breaks: inside recording gaps (crash/restart), pace is unknown. */
    val paceBreaksKm: List<Double> = emptyList(),
)
