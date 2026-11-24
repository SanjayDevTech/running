package com.example.running.setup

import android.app.PendingIntent
import android.content.Context
import com.example.running.core.contracts.AccuracyState
import com.example.running.core.contracts.LocationAccuracyChecker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Google Location Accuracy check (same approach as GeoFence's AccuracyFix): ask Play whether a
 * HIGH_ACCURACY request can be satisfied. RESOLUTION_REQUIRED means accuracy is off and Play
 * hands back a one-tap dialog. Requests no location. UNKNOWN on timeout, errors or no Play services.
 */
class PlayLocationAccuracyChecker(context: Context) : LocationAccuracyChecker {
    private val app = context.applicationContext

    @Volatile
    private var lastResolution: PendingIntent? = null

    override suspend fun check(): AccuracyState {
        if (!playAvailable()) return AccuracyState.UNKNOWN
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000).build())
            .build()
        lastResolution = null
        return withTimeoutOrNull(TIMEOUT_MS) {
            try {
                LocationServices.getSettingsClient(app).checkLocationSettings(request).await()
                AccuracyState.ON
            } catch (e: CancellationException) {
                throw e
            } catch (e: ResolvableApiException) {
                if (e.statusCode == CommonStatusCodes.RESOLUTION_REQUIRED) {
                    lastResolution = e.resolution
                    AccuracyState.OFF
                } else {
                    AccuracyState.UNKNOWN
                }
            } catch (_: Exception) {
                AccuracyState.UNKNOWN
            }
        } ?: AccuracyState.UNKNOWN
    }

    override suspend fun resolution(): PendingIntent? {
        if (lastResolution == null) check()
        return lastResolution
    }

    private fun playAvailable(): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) == ConnectionResult.SUCCESS
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
