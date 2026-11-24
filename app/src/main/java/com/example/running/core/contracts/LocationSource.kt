package com.example.running.core.contracts

import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.model.PressureSample
import com.example.running.core.model.StepCounterReading
import com.example.running.core.model.StepEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Implemented by track A (location/). */
interface LocationSource {
    val engine: LocationEngine

    /** PLAY: GMS present; PLATFORM: GPS provider exists. */
    fun isAvailable(): Boolean

    /** Cold flow; collection starts high-accuracy updates at [intervalMillis], cancellation stops them. */
    fun fixes(intervalMillis: Long = 1_000): Flow<LocationFix>
}

/** Implemented by track A (sensors/). */
interface StepSource {
    val hasStepDetector: Boolean
    val hasStepCounter: Boolean
    fun steps(): Flow<StepEvent>

    /** Raw step-counter readings (cumulative since boot); empty without a counter or permission. */
    fun counterReadings(): Flow<StepCounterReading> = emptyFlow()
}

/** Implemented by track A (sensors/). */
interface PressureSource {
    val isAvailable: Boolean

    /** ~1 Hz is enough. */
    fun pressure(): Flow<PressureSample>
}
