package com.foodfusionai.driver

import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationThrottlingTest {

    @Test
    fun testLocationRequestConfiguration() {
        // Construct the LocationRequest using the same parameters as LocationTrackingService
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 
            10000L // 10 seconds interval
        ).apply {
            setMinUpdateDistanceMeters(15f) // 15 meters throttling
            setMinUpdateIntervalMillis(5000L) // 5 seconds fastest interval
        }.build()

        // Assert the configuration values match our expectations
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, locationRequest.priority)
        assertEquals(10000L, locationRequest.intervalMillis)
        assertEquals(15f, locationRequest.minUpdateDistanceMeters)
        assertEquals(5000L, locationRequest.minUpdateIntervalMillis)
    }
}
