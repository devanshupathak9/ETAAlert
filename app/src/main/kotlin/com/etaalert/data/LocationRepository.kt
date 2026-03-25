package com.etaalert.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationRepository(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            continuation.resume(location)
                        } else {
                            requestFreshLocation(continuation)
                        }
                    }
                    .addOnFailureListener {
                        requestFreshLocation(continuation)
                    }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(
        continuation: kotlinx.coroutines.CancellableContinuation<Location?>
    ) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMaxUpdates(1)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocationClient.removeLocationUpdates(this)
                continuation.resume(result.lastLocation)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )

            continuation.invokeOnCancellation {
                fusedLocationClient.removeLocationUpdates(callback)
            }
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }
}
