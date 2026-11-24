package com.example.running.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.running.core.contracts.PressureSource
import com.example.running.core.contracts.StepSource
import com.example.running.core.model.PressureSample
import com.example.running.core.model.StepEvent
import com.example.running.core.system.Permissions
import com.example.running.core.util.Clock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/** SensorEvent.timestamp is on the elapsed-realtime base; converts it to wall-clock epoch millis. */
internal fun sensorEventWallMillis(eventNanos: Long, clock: Clock): Long =
    clock.nowMillis() - (clock.elapsedRealtimeNanos() - eventNanos) / 1_000_000

/** Turns cumulative STEP_COUNTER readings into deltas (the first reading is only a baseline). */
internal class StepCounterDeltas {
    private var last: Float? = null

    fun onReading(cumulative: Float): Int? {
        val prev = last
        last = cumulative
        if (prev == null || cumulative <= prev) return null
        return (cumulative - prev).toInt().takeIf { it > 0 }
    }
}

private fun Context.sensorManager(): SensorManager? = getSystemService(SensorManager::class.java)

/**
 * Step detector (one event per step) when present, else step counter deltas. Needs ACTIVITY_RECOGNITION;
 * without it the flow is empty.
 */
internal class AndroidStepSource(context: Context, private val clock: Clock) : StepSource {
    private val app = context.applicationContext

    override val hasStepDetector: Boolean get() = app.sensorManager()?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    override val hasStepCounter: Boolean get() = app.sensorManager()?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    override fun steps(): Flow<StepEvent> {
        val sm = app.sensorManager() ?: return emptyFlow()
        if (!Permissions.hasActivityRecognition(app)) return emptyFlow()
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: return emptyFlow()
        return callbackFlow {
            val deltas = StepCounterDeltas()
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val at = sensorEventWallMillis(event.timestamp, clock)
                    when (event.sensor.type) {
                        Sensor.TYPE_STEP_DETECTOR -> trySend(StepEvent(at, 1))
                        Sensor.TYPE_STEP_COUNTER -> deltas.onReading(event.values[0])?.let { trySend(StepEvent(at, it)) }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { sm.unregisterListener(listener) }
        }
    }
}

/** Barometer at ~1 Hz (for elevation). */
internal class AndroidPressureSource(context: Context, private val clock: Clock) : PressureSource {
    private val app = context.applicationContext

    override val isAvailable: Boolean get() = app.sensorManager()?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    override fun pressure(): Flow<PressureSample> {
        val sm = app.sensorManager() ?: return emptyFlow()
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return emptyFlow()
        return callbackFlow {
            var lastAt = Long.MIN_VALUE
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val at = sensorEventWallMillis(event.timestamp, clock)
                    if (at - lastAt < MIN_PRESSURE_INTERVAL_MILLIS) return
                    lastAt = at
                    trySend(PressureSample(at, event.values[0].toDouble()))
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(listener, sensor, PRESSURE_PERIOD_US)
            awaitClose { sm.unregisterListener(listener) }
        }
    }

    private companion object {
        const val PRESSURE_PERIOD_US = 1_000_000
        const val MIN_PRESSURE_INTERVAL_MILLIS = 900L
    }
}
