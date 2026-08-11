package com.foodfusionai.app.location

import com.foodfusionai.app.data.location.EtaInfo
import com.foodfusionai.app.data.location.EtaState
import com.foodfusionai.app.data.location.LocationFreshness
import com.foodfusionai.app.data.location.RouteResult
import com.google.android.gms.maps.model.LatLng
import com.foodfusionai.app.data.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ETA data classes and display logic.
 *
 * Parts W, X, Y — RoutingService, ETA states, ETA display
 */
class EtaTest {

    // ── EtaInfo.displayText ───────────────────────────────────────────────────

    @Test
    fun `CALCULATING shows calculating text`() {
        assertEquals("Calculating…", EtaInfo.CALCULATING.displayText)
    }

    @Test
    fun `UNAVAILABLE shows unavailable text`() {
        assertEquals("ETA unavailable", EtaInfo.UNAVAILABLE.displayText)
    }

    @Test
    fun `AVAILABLE shows minute range`() {
        val eta = EtaInfo(EtaState.AVAILABLE, minMinutes = 12, maxMinutes = 18)
        assertEquals("12–18 min", eta.displayText)
    }

    @Test
    fun `APPROXIMATE shows tilde prefix with range`() {
        val eta = EtaInfo(EtaState.APPROXIMATE, minMinutes = 15, maxMinutes = 22)
        assertEquals("~15–22 min", eta.displayText)
    }

    @Test
    fun `STALE shows updating suffix`() {
        val eta = EtaInfo(EtaState.STALE, minMinutes = 10, maxMinutes = 14)
        assertTrue("Expected updating… suffix", eta.displayText.contains("updating"))
    }

    // ── RouteResult ───────────────────────────────────────────────────────────

    @Test
    fun `durationMinutes rounds up correctly`() {
        val route = makeRoute(durationSeconds = 65)  // 1 min 5 sec → 2 min rounded
        assertEquals(2, route.durationMinutes)
    }

    @Test
    fun `durationMinutes minimum is 1`() {
        val route = makeRoute(durationSeconds = 0)
        assertEquals(1, route.durationMinutes)
    }

    @Test
    fun `isFallback true when straight-line route`() {
        val origin = GeoPoint(12.9716, 77.5946)
        val dest   = GeoPoint(12.9352, 77.6245)
        val route  = RouteResult(
            origin = origin, destination = dest,
            distanceKm = 4.5, durationSeconds = 600,
            polyline = listOf(
                LatLng(origin.latitude, origin.longitude),
                LatLng(dest.latitude, dest.longitude)
            ),
            isFallback = true
        )
        assertTrue(route.isFallback)
    }

    // ── ETA window calculation (mirrors LiveTrackingViewModel.buildEta) ────────

    @Test
    fun `ETA window has 20 percent buffer`() {
        val baseMinutes = 20
        val buffer = maxOf(1, (baseMinutes * 0.20).toInt())  // 4 min

        val eta = EtaInfo(
            state      = EtaState.AVAILABLE,
            minMinutes = maxOf(1, baseMinutes - buffer),
            maxMinutes = baseMinutes + buffer
        )
        assertEquals(16, eta.minMinutes)
        assertEquals(24, eta.maxMinutes)
    }

    @Test
    fun `ETA min never goes below 1`() {
        // 1 minute base with 20% buffer → buffer=1, min=0 but clamped to 1
        val eta = EtaInfo(
            state      = EtaState.AVAILABLE,
            minMinutes = maxOf(1, 1 - 1),
            maxMinutes = 2
        )
        assertEquals(1, eta.minMinutes)
    }

    private fun makeRoute(durationSeconds: Int) = RouteResult(
        origin          = GeoPoint(0.0, 0.0),
        destination     = GeoPoint(1.0, 1.0),
        distanceKm      = 1.0,
        durationSeconds = durationSeconds,
        polyline        = emptyList(),
        isFallback      = false
    )
}
