package com.foodfusionai.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps [FusedLocationProviderClient] in Kotlin-friendly suspend/Flow APIs.
 *
 * All callers MUST check location permission before calling any method here.
 * Permissions are managed by [LocationPermissionHelper].
 *
 * GPS tracking modes:
 *   [Mode.DELIVERY] — high-frequency, used by driver during active delivery
 *   [Mode.PASSIVE]  — low-frequency, used by customer to resolve their position
 */
class LocationProvider(context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // ── GPS availability ──────────────────────────────────────────────────────

    /**
     * Returns true when at least one location provider (GPS or Network) is enabled.
     */
    fun isLocationEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    // ── One-shot current location ──────────────────────────────────────────────

    /**
     * Suspends until a current location fix is obtained, then resumes.
     *
     * Throws [LocationUnavailableException] when GPS is disabled.
     *
     * Uses [Priority.PRIORITY_HIGH_ACCURACY] so the result includes GPS data;
     * the accuracy field on [LocationResult] reflects whether the fix was good.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationResult {
        if (!isLocationEnabled()) throw LocationUnavailableException("Location services disabled")

        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cts.cancel() }

            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        cont.resumeWithException(
                            LocationUnavailableException("Could not obtain a location fix")
                        )
                    } else {
                        cont.resume(location.toLocationResult())
                    }
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }
    }

    // ── Continuous updates ────────────────────────────────────────────────────

    /**
     * Returns a [Flow] of continuous location updates for the given [mode].
     *
     * The flow is backed by [FusedLocationProviderClient.requestLocationUpdates] and
     * automatically removes the callback when the coroutine is cancelled.
     *
     * Low-accuracy fixes (> 150 m) are filtered out to avoid misleading the UI.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(mode: Mode): Flow<LocationResult> = callbackFlow {
        if (!isLocationEnabled()) {
            close(LocationUnavailableException("Location services disabled"))
            return@callbackFlow
        }

        val request = mode.toLocationRequest()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { loc ->
                    // Filter out low-accuracy fixes for delivery tracking
                    if (mode == Mode.DELIVERY && loc.accuracy > MAX_DELIVERY_ACCURACY_M) return
                    trySend(loc.toLocationResult())
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    // ── Tracking modes ────────────────────────────────────────────────────────

    enum class Mode {
        /**
         * Active delivery tracking.
         * Updates every 7 seconds OR when the device moves > 10 m.
         * Uses HIGH_ACCURACY for reliable driver marker updates.
         */
        DELIVERY,

        /**
         * Customer location resolve (one-shot via [getCurrentLocation] is preferred,
         * but this mode can be used for the address-picker map to keep the blue dot live).
         * Updates every 30 seconds OR when device moves > 50 m.
         */
        PASSIVE
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun Mode.toLocationRequest(): LocationRequest =
        when (this) {
            Mode.DELIVERY -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 7_000L)
                .setMinUpdateIntervalMillis(5_000L)
                .setMinUpdateDistanceMeters(10f)
                .build()

            Mode.PASSIVE -> LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
                .setMinUpdateIntervalMillis(15_000L)
                .setMinUpdateDistanceMeters(50f)
                .build()
        }

    private fun android.location.Location.toLocationResult() = LocationResult(
        point = GeoPoint(latitude, longitude),
        accuracy = accuracy,
        heading = if (hasBearing()) bearing else null,
        speed = if (hasSpeed()) speed else null,
        timestamp = time
    )

    companion object {
        /** Fixes worse than 150 m are discarded during delivery tracking. */
        private const val MAX_DELIVERY_ACCURACY_M = 150f
    }
}

class LocationUnavailableException(message: String) : Exception(message)
