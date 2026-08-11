package com.foodfusionai.app.location

import com.foodfusionai.app.data.location.DistanceCalculator
import com.foodfusionai.app.data.location.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the client-side impossible-speed detection logic that mirrors
 * the server-side flagSuspiciousLocation Cloud Function.
 *
 * Parts AK, AL — GPS spoofing detection, impossible speed
 */
class LocationSpoofingTest {

    companion object {
        private const val MAX_SPEED_KMH = 120.0
    }

    /**
     * Compute speed km/h between two GPS fixes.
     * Mirrors geoUtils.speedKmh() from the Cloud Function.
     */
    private fun speedKmh(
        from: GeoPoint, fromTs: Long,
        to:   GeoPoint, toTs:   Long
    ): Double {
        val distKm     = DistanceCalculator.distanceKm(from, to)
        val deltaHours = (toTs - fromTs) / 3_600_000.0
        if (deltaHours <= 0) return 0.0
        return distKm / deltaHours
    }

    private fun isSuspicious(
        from: GeoPoint, fromTs: Long,
        to:   GeoPoint, toTs:   Long
    ) = speedKmh(from, fromTs, to, toTs) > MAX_SPEED_KMH

    // ── Normal movements should NOT be flagged ────────────────────────────────

    @Test
    fun `1km in 3 minutes is normal urban speed`() {
        val from = GeoPoint(12.9716, 77.5946)
        val to   = GeoPoint(12.9800, 77.6010) // ~1.2 km
        val fromTs = 0L
        val toTs   = 3 * 60_000L               // 3 minutes
        assertFalse(isSuspicious(from, fromTs, to, toTs))
    }

    @Test
    fun `5km in 10 minutes is 30 km/h, not suspicious`() {
        val from = GeoPoint(12.9716, 77.5946)
        val to   = GeoPoint(13.0165, 77.5946) // ~5 km north
        val fromTs = 0L
        val toTs   = 10 * 60_000L
        assertFalse(isSuspicious(from, fromTs, to, toTs))
    }

    @Test
    fun `same location is never suspicious`() {
        val p = GeoPoint(12.9716, 77.5946)
        assertFalse(isSuspicious(p, 0L, p, 10_000L))
    }

    // ── Impossible movements should be flagged ────────────────────────────────

    @Test
    fun `teleport 30km in 2 seconds is flagged as suspicious`() {
        val from = GeoPoint(12.9716, 77.5946) // Bangalore
        val to   = GeoPoint(13.2436, 77.7152) // ~30 km away
        val fromTs = 0L
        val toTs   = 2_000L                    // 2 seconds later
        assertTrue(isSuspicious(from, fromTs, to, toTs))
    }

    @Test
    fun `100km in 10 minutes exceeds 120 km/h threshold`() {
        val from = GeoPoint(12.9716, 77.5946)
        val to   = GeoPoint(13.8716, 77.5946) // ~100 km north
        val fromTs = 0L
        val toTs   = 10 * 60_000L
        assertTrue(isSuspicious(from, fromTs, to, toTs))
    }

    // ── Boundary at exactly 120 km/h ─────────────────────────────────────────

    @Test
    fun `exactly 120 km/h is NOT flagged (at threshold, not over)`() {
        // 2 km in exactly 60 seconds = 120 km/h
        val from   = GeoPoint(12.9716, 77.5946)
        val to     = GeoPoint(12.9896, 77.5946) // ~2 km north
        val fromTs = 0L
        val toTs   = 60_000L
        val speed  = speedKmh(from, fromTs, to, toTs)
        // speed ≈ 120 — at or under threshold
        assertFalse("Speed $speed should not be suspicious", speed > MAX_SPEED_KMH)
    }

    // ── Zero or negative time delta ───────────────────────────────────────────

    @Test
    fun `zero time delta returns zero speed and is not suspicious`() {
        val from = GeoPoint(12.9716, 77.5946)
        val to   = GeoPoint(13.2436, 77.7152)
        assertFalse(isSuspicious(from, 0L, to, 0L)) // same timestamp
    }
}
