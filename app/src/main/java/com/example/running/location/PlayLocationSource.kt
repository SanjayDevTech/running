package com.example.running.location

import android.annotation.SuppressLint
import android.content.Context
import com.example.running.core.contracts.LocationSource
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.system.CallbackThread
import com.example.running.core.system.Permissions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** "Google" engine: fused location provider at high accuracy, 1 s (architecture.md §3.2). */
internal class PlayLocationSource(context: Context) : LocationSource {
    private val app = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(app) }

    override val engine: LocationEngine = LocationEngine.PLAY

    override fun isAvailable(): Boolean =
        runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) == ConnectionResult.SUCCESS }
            .getOrDefault(false)

    @SuppressLint("MissingPermission") // checked at collection time; a revoke throws and the manager retries
    override fun fixes(intervalMillis: Long): Flow<LocationFix> = callbackFlow {
        if (!Permissions.hasFineLocation(app)) throw SecurityException("Fine location not granted")
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toFix(engine)) }
            }
        }
        client.requestLocationUpdates(request, callback, CallbackThread.looper)
            .addOnFailureListener { close(it) }
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
