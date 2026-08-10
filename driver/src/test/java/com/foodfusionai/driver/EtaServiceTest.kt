package com.foodfusionai.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.*

/**
 * Tests the ETA calculation logic used by calculateDeliveryEta Cloud Function.
 * Validates Haversine distance math and velocity-based travel time estimates.
 */
class EtaServiceTest {

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    // Speed profiles in km/minute (matching Cloud Function)
    private val bikeSpeed = 25.0 / 60.0     // 0.416 km/min
    private val scooterSpeed = 30.0 / 60.0  // 0.5 km/min
    private val carSpeed = 40.0 / 60.0      // 0.666 km/min
    private val bicycleSpeed = 15.0 / 60.0  // 0.25 km/min

    private fun calculateTravelMins(distanceKm: Double, speedKmPerMin: Double): Double {
        return distanceKm / speedKmPerMin
    }

    @Test
    fun testBikeEta_shortDistance() {
        val dist = 2.0 // 2 km
        val mins = calculateTravelMins(dist, bikeSpeed)
        // 2 km at 25 km/h = 4.8 minutes
        assertEquals(4.8, mins, 0.1)
    }

    @Test
    fun testScooterEta_shortDistance() {
        val dist = 2.0
        val mins = calculateTravelMins(dist, scooterSpeed)
        // 2 km at 30 km/h = 4.0 minutes
        assertEquals(4.0, mins, 0.1)
    }

    @Test
    fun testCarEta_shortDistance() {
        val dist = 2.0
        val mins = calculateTravelMins(dist, carSpeed)
        // 2 km at 40 km/h = 3.0 minutes
        assertEquals(3.0, mins, 0.1)
    }

    @Test
    fun testBicycleEta_shortDistance() {
        val dist = 2.0
        val mins = calculateTravelMins(dist, bicycleSpeed)
        // 2 km at 15 km/h = 8.0 minutes
        assertEquals(8.0, mins, 0.1)
    }

    @Test
    fun testTotalDeliveryEta_withPrepTime() {
        val driverToRestaurant = 1.5 // km
        val restaurantToCustomer = 3.0 // km
        val prepTimeMins = 20.0

        val pickupTravelMins = calculateTravelMins(driverToRestaurant, bikeSpeed)
        val deliveryTravelMins = calculateTravelMins(restaurantToCustomer, bikeSpeed)

        // Total = max(prep, pickup travel) + delivery travel
        val totalMins = maxOf(prepTimeMins, pickupTravelMins) + deliveryTravelMins

        // Prep (20 min) > pickup travel (3.6 min), so total = 20 + 7.2 = 27.2
        assertTrue("Total ETA should be ~27 mins, got $totalMins", totalMins in 25.0..30.0)
    }

    @Test
    fun testTotalDeliveryEta_driverFaraway() {
        val driverToRestaurant = 4.0 // km (far)
        val restaurantToCustomer = 2.0 // km
        val prepTimeMins = 5.0 // very short prep

        val pickupTravelMins = calculateTravelMins(driverToRestaurant, bikeSpeed)
        val deliveryTravelMins = calculateTravelMins(restaurantToCustomer, bikeSpeed)

        // Pickup travel (9.6 min) > prep (5 min), so total = 9.6 + 4.8 = 14.4
        val totalMins = maxOf(prepTimeMins, pickupTravelMins) + deliveryTravelMins
        assertTrue("Total ETA should be ~14 mins, got $totalMins", totalMins in 12.0..17.0)
    }

    @Test
    fun testRealWorldDistance_koramangalaToIndiranagar() {
        // Koramangala to Indiranagar, Bangalore (~3.5 km)
        val dist = calculateDistance(12.9352, 77.6245, 12.9784, 77.6408)
        val bikeMins = calculateTravelMins(dist, bikeSpeed)
        assertTrue("Should take ~12 mins by bike, got $bikeMins", bikeMins in 8.0..18.0)
    }
}
