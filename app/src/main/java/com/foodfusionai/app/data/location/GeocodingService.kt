package com.foodfusionai.app.data.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Translates between coordinates and human-readable addresses using the
 * Android [Geocoder] backed by the Google Geocoding API.
 *
 * On Android 33+ [Geocoder.getFromLocation] is async; on older versions we
 * call the blocking overload in a coroutine (callers should already be on IO).
 *
 * Failure modes handled:
 *   - Network unavailable         → [Resource.Error] with user-friendly message
 *   - No result found             → [Resource.Error] "Address not found"
 *   - Ambiguous / multiple result → first result returned (most relevant)
 *   - Geocoder unavailable        → [Resource.Error] with guidance
 */
class GeocodingService(private val context: Context) {

    private val geocoder = Geocoder(context, Locale.getDefault())

    // ── Reverse geocode: coordinates → address ────────────────────────────────

    /**
     * Returns a [ResolvedAddress] for the given [point], or an error.
     */
    suspend fun reverseGeocode(point: GeoPoint): Resource<ResolvedAddress> {
        if (!Geocoder.isPresent()) {
            return Resource.Error("Geocoder service not available on this device")
        }

        return try {
            val addresses = fetchAddresses(point)
            if (addresses.isNullOrEmpty()) {
                Resource.Error("Address not found for this location")
            } else {
                Resource.Success(addresses.first().toResolvedAddress(point))
            }
        } catch (e: IOException) {
            Resource.Error("Network error — could not reverse geocode location")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Geocoding failed")
        }
    }

    // ── Forward geocode: address string → coordinates ─────────────────────────

    /**
     * Returns the best [GeoPoint] for a given address string, or an error.
     *
     * Primarily used when importing an address that has no stored coordinates.
     */
    suspend fun geocode(addressText: String): Resource<GeoPoint> {
        if (!Geocoder.isPresent()) {
            return Resource.Error("Geocoder service not available on this device")
        }

        return try {
            val results = geocodeAddress(addressText)
            if (results.isNullOrEmpty()) {
                Resource.Error("Could not find coordinates for '$addressText'")
            } else {
                val first = results.first()
                Resource.Success(GeoPoint(first.latitude, first.longitude))
            }
        } catch (e: IOException) {
            Resource.Error("Network error — could not geocode address")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Geocoding failed")
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private suspend fun fetchAddresses(point: GeoPoint): List<android.location.Address>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Async API on Android 13+
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(point.latitude, point.longitude, 1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<android.location.Address>) =
                            cont.resume(addresses)
                        override fun onError(errorMessage: String?) =
                            cont.resume(emptyList())
                    }
                )
            }
        } else {
            // Blocking call — caller must be on IO dispatcher
            geocoder.getFromLocation(point.latitude, point.longitude, 1)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun geocodeAddress(address: String): List<android.location.Address>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(address, 1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<android.location.Address>) =
                            cont.resume(addresses)
                        override fun onError(errorMessage: String?) =
                            cont.resume(emptyList())
                    }
                )
            }
        } else {
            geocoder.getFromLocationName(address, 1)
        }
    }

    private fun android.location.Address.toResolvedAddress(point: GeoPoint) = ResolvedAddress(
        point = point,
        shortName = getAddressLine(0) ?: "",
        locality = locality ?: subLocality ?: "",
        city = adminArea ?: "",
        state = adminArea ?: "",
        postalCode = postalCode ?: "",
        country = countryName ?: "",
        countryCode = countryCode ?: "",
        fullAddress = (0..maxAddressLineIndex).joinToString(", ") { getAddressLine(it) }
    )
}

/**
 * A structured address resolved from or to real-world coordinates.
 */
data class ResolvedAddress(
    val point: GeoPoint,
    val shortName: String,       // e.g. "123 MG Road"
    val locality: String,        // e.g. "Koramangala"
    val city: String,            // e.g. "Bengaluru"
    val state: String,
    val postalCode: String,
    val country: String,
    val countryCode: String,
    val fullAddress: String      // full formatted string
) {
    /** Short display label for home screen header: "Koramangala, Bengaluru". */
    val displayLabel: String
        get() = when {
            locality.isNotBlank() && city.isNotBlank() -> "$locality, $city"
            city.isNotBlank() -> city
            else -> shortName
        }
}
