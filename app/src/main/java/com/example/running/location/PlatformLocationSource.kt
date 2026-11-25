package com.example.running.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import com.example.running.core.contracts.LocationSource
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.system.CallbackThread
import com.example.running.core.system.Permissions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** "Built-in" engine: the platform GPS provider, 1 s / 0 m, no Google Play needed (architecture.md §3.2). */
internal class PlatformLocationSource(context: Context) : LocationSource {
    private val app = context.applicationContext
    private val manager: LocationManager? get() = app.getSystemService(LocationManager::class.java)

    override val engine: LocationEngine = LocationEngine.PLATFORM

    override fun isAvailable(): Boolean = manager?.allProviders?.contains(LocationManager.GPS_PROVIDER) == true

    @SuppressLint("MissingPermission") // checked at collection time; a revoke throws and the manager retries
    override fun fixes(intervalMillis: Long): Flow<LocationFix> = callbackFlow {
        val lm = manager ?: throw IllegalStateException("No LocationManager")
        if (!Permissions.hasFineLocation(app)) throw SecurityException("Fine location not granted")
        val request = LocationRequest.Builder(intervalMillis)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(intervalMillis)
            .setMinUpdateDistanceMeters(0f)
            .build()
        val listener = LocationListener { trySend(it.toFix(engine)) }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, request, CallbackThread.executor, listener)
        awaitClose { lm.removeUpdates(listener) }
    }
}
