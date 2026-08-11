package com.foodfusionai.app.location

import com.foodfusionai.app.data.location.DistanceCalculator
import com.foodfusionai.app.data.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for [DistanceCalculator].
 *
 * Reference distances verified against online Haversine calculators.
 */
class DistanceCalculatorTest {

    // Bangalore city centre
    private val bangalore = GeoPoint(12.9716, 77.5946)
    // Koramangala (≈ 3.8 km from city centre via Haversine)
    private val koramangala = GeoPoint(12.9352, 77.6245)
    // Chennai (≈ 290 km from Bangalore)
    private val chennai = GeoPoint(13.0827, 80.2707)

    // ── distanceKm ────────────────────────────────────────────────────────────

    @Test
    fun `same point returns zero distance`() {
        val dist = DistanceCalculator.distanceKm(bangalore, bangalore)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun `Bangalore to Koramangala is approximately 4km`() {
        val dist = DistanceCalculator.distanceKm(bangalore, koramangala)
        assertTrue("Expected ~4 km but got $dist", abs(dist - 4.0) < 1.0)
    }

    @Test
    fun `Bangalore to Chennai is approximately 290km`() {
        val dist = DistanceCalculator.distanceKm(bangalore, chennai)
        assertTrue("Expected ~290 km but got $dist", abs(dist - 290.0) < 5.0)
    }

    @Test
    fun `distance is symmetric`() {
        val ab = DistanceCalculator.distanceKm(bangalore, chennai)
        val ba = DistanceCalculator.distanceKm(chennai, bangalore)
        assertEquals(ab, ba, 0.001)
    }

    // ── distanceMetres ────────────────────────────────────────────────────────

    @Test
    fun `distanceMetres equals distanceKm times 1000`() {
        val km = DistanceCalculator.distanceKm(bangalore, koramangala)
        val m  = DistanceCalculator.distanceMetres(bangalore, koramangala)
        assertEquals(km * 1000.0, m, 0.1)
    }

    // ── formatDistance ────────────────────────────────────────────────────────

    @Test
    fun `formatDistance under 1km shows metres`() {
        val near = GeoPoint(12.9716, 77.5950)
        val label = DistanceCalculator.formatDistance(bangalore, near)
        assertTrue("Expected metres format, got: $label", label.endsWith(" m"))
    }

    @Test
    fun `formatDistance between 1 and 10km shows one decimal km`() {
        val label = DistanceCalculator.formatDistance(bangalore, koramangala)
        assertTrue("Expected X.X km format, got: $label",
            label.matches(Regex("\\d+\\.\\d km")))
    }

    @Test
    fun `formatDistance over 10km shows whole km`() {
        val label = DistanceCalculator.formatDistance(bangalore, chennai)
        assertTrue("Expected whole-number km format, got: $label",
            label.matches(Regex("\\d+ km")))
    }

    // ── bearingDegrees ────────────────────────────────────────────────────────

    @Test
    fun `bearing is within 0 to 360 range`() {
        val bearing = DistanceCalculator.bearingDegrees(bangalore, chennai)
        assertTrue("Bearing out of range: $bearing", bearing in 0f..360f)
    }

    @Test
    fun `north bearing is approximately 0 degrees`() {
        val south = GeoPoint(12.0, 77.5946)
        val north = GeoPoint(14.0, 77.5946)
        val bearing = DistanceCalculator.bearingDegrees(south, north)
        assertTrue("Expected ~0° bearing, got: $bearing", bearing < 5f || bearing > 355f)
    }

    // ── straightLineEtaMinutes ────────────────────────────────────────────────

    @Test
    fun `5km at 25kmh is 12 minutes`() {
        val eta = DistanceCalculator.straightLineEtaMinutes(5.0, 25.0)
        assertEquals(12, eta)
    }

    @Test
    fun `zero distance returns 1 minute minimum`() {
        val eta = DistanceCalculator.straightLineEtaMinutes(0.0)
        assertEquals(1, eta)
    }

    @Test
    fun `negative speed returns MAX_VALUE`() {
        val eta = DistanceCalculator.straightLineEtaMinutes(5.0, -1.0)
        assertEquals(Int.MAX_VALUE, eta)
    }
}
