package com.foodfusionai.app.data.repository

import com.foodfusionai.app.utils.Resource

/**
 * Interface contract for fetching GPS location and reverse geocoding it to an address string.
 */
interface LocationRepository {

    /**
     * Fetches current latitude and longitude.
     * Needs permissions to be granted prior to calling.
     */
    suspend fun getCurrentLocation(): Resource<Pair<Double, Double>>

    /**
     * Converts coordinates into a human-readable address.
     */
    suspend fun getAddressFromCoordinates(latitude: Double, longitude: Double): Resource<String>
}
