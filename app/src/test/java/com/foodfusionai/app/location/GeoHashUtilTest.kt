package com.foodfusionai.app.location

import com.foodfusionai.app.data.location.GeoHashUtil
import com.foodfusionai.app.data.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GeoHashUtil].
 *
 * Reference geohashes validated against https://www.geohash.org/
 * Part K — Geohash strategy
 */
class GeoHashUtilTest {

    // ── encode ────────────────────────────────────────────────────────────────

    @Test
    fun `encode Bangalore at precision 5 starts with tdr`() {
        // Bangalore (12.9716, 77.5946) → geohash precision-5 prefix = "tdr1w" area
        val hash = GeoHashUtil.encode(12.9716, 77.5946, precision = 5)
        assertEquals(5, hash.length)
        assertTrue("Expected geohash to start with 'tdr', got: $hash",
            hash.startsWith("tdr"))
    }

    @Test
    fun `encode returns correct length`() {
        for (p in 1..9) {
            val hash = GeoHashUtil.encode(12.9716, 77.5946, precision = p)
            assertEquals("Wrong length at precision $p", p, hash.length)
        }
    }

    @Test
    fun `encode only uses base32 characters`() {
        val base32 = "0123456789bcdefghjkmnpqrstuvwxyz".toSet()
        val hash = GeoHashUtil.encode(12.9716, 77.5946, precision = 9)
        hash.forEach { ch ->
            assertTrue("Unexpected character '$ch' in geohash", ch in base32)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode with precision 0 throws`() {
        GeoHashUtil.encode(12.9716, 77.5946, precision = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode with lat out of range throws`() {
        GeoHashUtil.encode(91.0, 77.5946)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode with lon out of range throws`() {
        GeoHashUtil.encode(12.9716, 181.0)
    }

    @Test
    fun `extreme coordinates encode without exception`() {
        GeoHashUtil.encode(-90.0, -180.0)
        GeoHashUtil.encode(90.0, 180.0)
        GeoHashUtil.encode(0.0, 0.0)
    }

    // ── queryPrefixes ─────────────────────────────────────────────────────────

    @Test
    fun `queryPrefixes returns between 1 and 9 unique prefixes`() {
        val center = GeoPoint(12.9716, 77.5946)
        val prefixes = GeoHashUtil.queryPrefixes(center, radiusKm = 5.0, precision = 5)
        assertTrue("Too few prefixes: ${prefixes.size}", prefixes.size >= 1)
        assertTrue("Too many prefixes: ${prefixes.size}", prefixes.size <= 9)
    }

    @Test
    fun `queryPrefixes are sorted`() {
        val center = GeoPoint(12.9716, 77.5946)
        val prefixes = GeoHashUtil.queryPrefixes(center, radiusKm = 5.0, precision = 5)
        assertEquals(prefixes.sorted(), prefixes)
    }

    @Test
    fun `queryPrefixes are unique`() {
        val center = GeoPoint(12.9716, 77.5946)
        val prefixes = GeoHashUtil.queryPrefixes(center, radiusKm = 5.0, precision = 5)
        assertEquals(prefixes.size, prefixes.distinct().size)
    }

    @Test
    fun `queryPrefixes all have correct precision`() {
        val center = GeoPoint(12.9716, 77.5946)
        val prefixes = GeoHashUtil.queryPrefixes(center, radiusKm = 5.0, precision = 5)
        prefixes.forEach { p ->
            assertEquals("Prefix has wrong length: $p", 5, p.length)
        }
    }

    // ── rangeForPrefix ────────────────────────────────────────────────────────

    @Test
    fun `rangeForPrefix start equals prefix`() {
        val (start, _) = GeoHashUtil.rangeForPrefix("tdr1w")
        assertEquals("tdr1w", start)
    }

    @Test
    fun `rangeForPrefix end has unicode suffix`() {
        val (_, end) = GeoHashUtil.rangeForPrefix("tdr1w")
        assertTrue("Expected end to contain \\uf8ff", end.endsWith("\uf8ff"))
    }

    // ── GeoPoint encode convenience ───────────────────────────────────────────

    @Test
    fun `encode GeoPoint matches encode lat lon`() {
        val point = GeoPoint(12.9716, 77.5946)
        assertEquals(
            GeoHashUtil.encode(12.9716, 77.5946, 9),
            GeoHashUtil.encode(point, 9)
        )
    }
}
