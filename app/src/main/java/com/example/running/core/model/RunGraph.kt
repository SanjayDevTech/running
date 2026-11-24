package com.example.running.core.model

/** One run with every child row; unit of backup/import and demo seeding. */
data class RunGraph(
    val run: Run,
    val points: List<TrackPoint>,
    val pauses: List<PauseInterval>,
    val splits: List<Split>,
    val segments: List<Segment>,
    val bestEfforts: List<BestEffort>,
    val sensorSamples: List<SensorSample>,
)
