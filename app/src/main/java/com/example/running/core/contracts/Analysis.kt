package com.example.running.core.contracts

import com.example.running.core.model.ActivityType
import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.LiveStats
import com.example.running.core.model.LocationFix
import com.example.running.core.model.MapRoute
import com.example.running.core.model.PressureSample
import com.example.running.core.model.RunAnalysis
import com.example.running.core.model.RunSeries
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.SensorSample
import com.example.running.core.model.StepEvent
import com.example.running.core.model.TrackPoint

// Implemented by track B (analysis/, pure Kotlin).

interface RunAnalyzer {
    fun analyze(input: AnalysisInput): RunAnalysis
}

interface LiveMetrics {
    fun onFix(fix: LocationFix, paused: Boolean)
    fun onSteps(event: StepEvent, paused: Boolean)
    fun onPressure(sample: PressureSample)
    fun onPauseChanged(paused: Boolean, atMillis: Long)
    fun snapshot(nowMillis: Long): LiveStats
    /** Live polyline (pace colouring optional). */
    fun acceptedRoute(): MapRoute
}

interface LiveMetricsFactory {
    fun create(profile: RunnerProfile, startedAtMillis: Long): LiveMetrics
}

/** "Morning Run", "Evening Walk"… */
interface RunNamer {
    fun name(startedAtMillis: Long, zoneId: String, type: ActivityType): String
}

interface RunSeriesBuilder {
    /** [sensorSamples] (the run's step samples) are only needed for the cadence series. */
    fun build(filteredPoints: List<TrackPoint>, sensorSamples: List<SensorSample> = emptyList()): RunSeries
}
