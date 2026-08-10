package com.foodfusionai.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.*

/**
 * Tests the Haversine distance calculation and dispatch scoring algorithm
 * used by the dispatchReadyOrder Cloud Function.
 */
class DispatchScoringTest {

    // Haversine formula (same as Cloud Function implementation)
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

    // Scoring function (mirrors Cloud Function logic)
    private fun calculateScore(
        distance: Double,
        reliability: Double = 90.0,
        activeDeliveries: Int = 0,
        distanceWeight: Double = 0.4,
        reliabilityWeight: Double = 0.2,
        workloadWeight: Double = 0.2,
        etaWeight: Double = 0.2
    ): Double {
        val distanceScore = maxOf(0.0, 100.0 - (distance * 15.0))
        val reliabilityScore = reliability
        val workloadScore = if (activeDeliveries == 0) 100.0 else 50.0
        val estMinutes = distance * 2.4
        val etaScore = maxOf(0.0, 100.0 - (estMinutes * 5.0))

        return (distanceScore * distanceWeight) +
               (reliabilityScore * reliabilityWeight) +
               (workloadScore * workloadWeight) +
               (etaScore * etaWeight)
    }

    @Test
    fun testHaversineDistance_samePoint() {
        val dist = calculateDistance(12.9716, 77.5946, 12.9716, 77.5946)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun testHaversineDistance_nearbyPoints() {
        // Koramangala to MG Road, Bangalore (~4 km)
        val dist = calculateDistance(12.9352, 77.6245, 12.9757, 77.6065)
        assertTrue("Distance should be approximately 4-5 km, got $dist", dist in 3.0..6.0)
    }

    @Test
    fun testHaversineDistance_farPoints() {
        // Bangalore to Chennai (~290 km)
        val dist = calculateDistance(12.9716, 77.5946, 13.0827, 80.2707)
        assertTrue("Distance should be approximately 280-310 km, got $dist", dist in 270.0..320.0)
    }

    @Test
    fun testScore_closestDriverWins() {
        val scoreA = calculateScore(distance = 1.2, reliability = 90.0, activeDeliveries = 0)
        val scoreB = calculateScore(distance = 3.0, reliability = 90.0, activeDeliveries = 0)
        assertTrue("Closer driver should score higher: $scoreA > $scoreB", scoreA > scoreB)
    }

    @Test
    fun testScore_busyDriverPenalized() {
        val scoreIdle = calculateScore(distance = 2.0, reliability = 90.0, activeDeliveries = 0)
        val scoreBusy = calculateScore(distance = 2.0, reliability = 90.0, activeDeliveries = 1)
        assertTrue("Idle driver should score higher: $scoreIdle > $scoreBusy", scoreIdle > scoreBusy)
    }

    @Test
    fun testScore_highReliabilityBoost() {
        val scoreHigh = calculateScore(distance = 2.0, reliability = 95.0, activeDeliveries = 0)
        val scoreLow = calculateScore(distance = 2.0, reliability = 60.0, activeDeliveries = 0)
        assertTrue("Higher reliability should score higher: $scoreHigh > $scoreLow", scoreHigh > scoreLow)
    }

    @Test
    fun testScore_tooFarDriverGetsLowScore() {
        val score = calculateScore(distance = 7.0, reliability = 90.0, activeDeliveries = 0)
        assertTrue("Driver 7km away should have low score: $score", score < 50.0)
    }

    @Test
    fun testScore_weightsAreRespected() {
        // With all distance weight
        val scoreAllDist = calculateScore(
            distance = 1.0,
            distanceWeight = 1.0,
            reliabilityWeight = 0.0,
            workloadWeight = 0.0,
            etaWeight = 0.0
        )
        val expectedDist = maxOf(0.0, 100.0 - (1.0 * 15.0)) * 1.0
        assertEquals(expectedDist, scoreAllDist, 0.01)
    }
}
