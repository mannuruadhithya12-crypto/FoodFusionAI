package com.foodfusionai.app.location

import com.foodfusionai.app.data.location.DistanceCalculator
import com.foodfusionai.app.data.location.GeoPoint
import com.foodfusionai.app.data.models.Restaurant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for delivery radius / deliverability logic.
 *
 * Part M — Delivery radius
 * Part L — Nearby restaurant deliverability
 *
 * These tests exercise the pure Kotlin delivery-radius logic.
 * The authoritative server check is in validateDeliveryLocation Cloud Function.
 */
class DeliveryRadiusTest {

    // Restaurant at Koramangala, Bangalore
    private val restaurantLocation = GeoPoint(12.9352, 77.6245)

    // Helpers
    private fun makeRestaurant(radiusKm: Double) = Restaurant(
        id               = "r1",
        name             = "Test Restaurant",
        latitude         = restaurantLocation.latitude,
        longitude        = restaurantLocation.longitude,
        deliveryRadiusKm = radiusKm
    )

    private fun isDeliverable(customer: GeoPoint, restaurant: Restaurant): Boolean {
        val distKm = DistanceCalculator.distanceKm(customer, restaurantLocation)
        return distKm <= restaurant.effectiveDeliveryRadiusKm
    }

    // ── Within radius ──────────────────────────────────────────────────────────

    @Test
    fun `customer 1km away is deliverable within 6km radius`() {
        // ~1 km north of restaurant
        val customer = GeoPoint(12.9442, 77.6245)
        assertTrue(isDeliverable(customer, makeRestaurant(6.0)))
    }

    @Test
    fun `customer at restaurant location is always deliverable`() {
        assertTrue(isDeliverable(restaurantLocation, makeRestaurant(1.0)))
    }

    // ── Outside radius ────────────────────────────────────────────────────────

    @Test
    fun `customer 8km away is NOT deliverable within 6km radius`() {
        // 8.1 km away (Indiranagar area)
        val customer = GeoPoint(12.9784, 77.6408)
        assertFalse(isDeliverable(customer, makeRestaurant(6.0)))
    }

    @Test
    fun `customer outside tight 2km radius is not deliverable`() {
        val customer = GeoPoint(12.9442, 77.6245) // ~1 km away
        // With 0.5 km radius, this should be outside
        assertFalse(isDeliverable(customer, makeRestaurant(0.5)))
    }

    // ── Default radius fallback ───────────────────────────────────────────────

    @Test
    fun `restaurant with zero radius uses 8km default`() {
        val restaurant = makeRestaurant(0.0)
        assertEquals(8.0, restaurant.effectiveDeliveryRadiusKm, 0.001)
    }

    @Test
    fun `restaurant with negative radius uses 8km default`() {
        val restaurant = makeRestaurant(-1.0)
        assertEquals(8.0, restaurant.effectiveDeliveryRadiusKm, 0.001)
    }

    // ── hasCoordinates ────────────────────────────────────────────────────────

    @Test
    fun `restaurant with real coordinates reports hasCoordinates true`() {
        assertTrue(makeRestaurant(6.0).hasCoordinates)
    }

    @Test
    fun `restaurant with zero coordinates reports hasCoordinates false`() {
        val r = Restaurant(id = "r2", name = "No Coords", latitude = 0.0, longitude = 0.0)
        assertFalse(r.hasCoordinates)
    }

    private fun assertEquals(expected: Double, actual: Double, delta: Double) {
        assertTrue("Expected $expected ± $delta but got $actual",
            kotlin.math.abs(expected - actual) <= delta)
    }
}
