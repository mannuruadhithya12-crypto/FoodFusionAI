package com.foodfusionai.app.location

import com.foodfusionai.app.data.location.LocationFreshness
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [LocationFreshness.classify] and [LocationFreshness.ageLabel].
 *
 * Part S — Location freshness detection
 */
class LocationFreshnessTest {

    private val now = System.currentTimeMillis()

    // ── classify ──────────────────────────────────────────────────────────────

    @Test
    fun `null timestamp returns OFFLINE`() {
        assertEquals(LocationFreshness.OFFLINE, LocationFreshness.classify(null, now))
    }

    @Test
    fun `zero timestamp returns OFFLINE`() {
        assertEquals(LocationFreshness.OFFLINE, LocationFreshness.classify(0L, now))
    }

    @Test
    fun `negative timestamp returns OFFLINE`() {
        assertEquals(LocationFreshness.OFFLINE, LocationFreshness.classify(-1L, now))
    }

    @Test
    fun `30 seconds old returns HEALTHY`() {
        val ts = now - 30_000L
        assertEquals(LocationFreshness.HEALTHY, LocationFreshness.classify(ts, now))
    }

    @Test
    fun `exactly 60 seconds old returns HEALTHY`() {
        val ts = now - 60_000L
        assertEquals(LocationFreshness.HEALTHY, LocationFreshness.classify(ts, now))
    }

    @Test
    fun `61 seconds old returns STALE`() {
        val ts = now - 61_000L
        assertEquals(LocationFreshness.STALE, LocationFreshness.classify(ts, now))
    }

    @Test
    fun `3 minutes old returns STALE`() {
        val ts = now - 3 * 60_000L
        assertEquals(LocationFreshness.STALE, LocationFreshness.classify(ts, now))
    }

    @Test
    fun `exactly 5 minutes old returns STALE`() {
        val ts = now - 300_000L
        assertEquals(LocationFreshness.STALE, LocationFreshness.classify(ts, now))
    }

    @Test
    fun `5 minutes 1 second old returns OFFLINE`() {
        val ts = now - 300_001L
        assertEquals(LocationFreshness.OFFLINE, LocationFreshness.classify(ts, now))
    }

    @Test
    fun `10 minutes old returns OFFLINE`() {
        val ts = now - 10 * 60_000L
        assertEquals(LocationFreshness.OFFLINE, LocationFreshness.classify(ts, now))
    }

    // ── ageLabel ──────────────────────────────────────────────────────────────

    @Test
    fun `null timestamp ageLabel returns unavailable`() {
        val label = LocationFreshness.ageLabel(null, now)
        assertEquals("Location unavailable", label)
    }

    @Test
    fun `5 seconds old ageLabel returns just now`() {
        val label = LocationFreshness.ageLabel(now - 5_000L, now)
        assertEquals("Updated just now", label)
    }

    @Test
    fun `45 seconds old ageLabel contains seconds`() {
        val label = LocationFreshness.ageLabel(now - 45_000L, now)
        assert(label.contains("45s")) { "Expected '45s' in: $label" }
    }

    @Test
    fun `3 minutes old ageLabel contains min`() {
        val label = LocationFreshness.ageLabel(now - 3 * 60_000L, now)
        assert(label.contains("3 min")) { "Expected '3 min' in: $label" }
    }
}
