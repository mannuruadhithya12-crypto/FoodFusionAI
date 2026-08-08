package com.foodfusionai.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.foodfusionai.app.utils.Resource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

class LocationRepositoryImpl(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
) : LocationRepository {

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Resource<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        try {
            val location: Location? = fusedLocationClient.lastLocation.await()
            if (location != null) {
                Resource.Success(Pair(location.latitude, location.longitude))
            } else {
                Resource.Error("Could not determine location. Ensure location services are enabled.")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to get location.")
        }
    }

    override suspend fun getAddressFromCoordinates(latitude: Double, longitude: Double): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val addressText = buildString {
                    val featureName = address.featureName
                    if (featureName != null && !featureName.matches(Regex("^[0-9].*"))) {
                        append(featureName).append(", ")
                    }
                    if (address.thoroughfare != null) append(address.thoroughfare).append(", ")
                    if (address.subLocality != null) append(address.subLocality).append(", ")
                    if (address.locality != null) append(address.locality)
                }
                
                val finalAddress = if (addressText.isNotBlank()) addressText else address.getAddressLine(0)
                Resource.Success(finalAddress ?: "Unknown Location")
            } else {
                Resource.Error("Could not resolve address.")
            }
        } catch (e: Exception) {
            Resource.Error("Geocoder failed: ${e.message}")
        }
    }
}
