package com.foodfusionai.app.data.location

import com.foodfusionai.app.utils.Resource
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Computes road routes using the Google Maps Directions API.
 *
 * ── Cost control ──────────────────────────────────────────────────────────────
 * Routing requests are the most expensive Maps API call (≈$5 per 1000).
 * Strategy:
 *   1. Route results are cached in memory keyed by (origin, destination) rounded
 *      to 4 decimal places (~11 m precision) so that small GPS jitter doesn't
 *      trigger a new API call.
 *   2. Cache entries expire after [CACHE_TTL_MS] (5 minutes).
 *   3. When the API key is absent / rate-limited / offline, the service falls
 *      back to a Haversine straight-line estimate so the UI always shows
 *      *something* useful.
 *
 * ── Provider independence ─────────────────────────────────────────────────────
 * All map-rendering code accepts [RouteResult] and never calls this service
 * directly — they go through [LiveTrackingViewModel] which owns the routing
 * lifecycle.  Swapping to a different routing provider only requires updating
 * this class.
 *
 * @param apiKey  Your Maps Platform API key.  Pass an empty string to force
 *                fallback mode (useful for testing without quota).
 */
class RoutingService(private val apiKey: String) {

    // ── In-memory route cache ─────────────────────────────────────────────────

    private data class CacheKey(val ox: String, val oy: String, val dx: String, val dy: String)
    private data class CacheEntry(val result: RouteResult, val expiresAt: Long)

    private val cache = HashMap<CacheKey, CacheEntry>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a [RouteResult] from [origin] to [destination].
     *
     * Uses cached result when available.
     * Falls back to Haversine straight-line when API is unavailable.
     */
    suspend fun getRoute(origin: GeoPoint, destination: GeoPoint): Resource<RouteResult> {
        val key = cacheKey(origin, destination)
        val cached = cache[key]
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return Resource.Success(cached.result)
        }

        return if (apiKey.isBlank()) {
            Resource.Success(straightLineFallback(origin, destination))
        } else {
            try {
                val result = withContext(Dispatchers.IO) { fetchRoute(origin, destination) }
                cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
                Resource.Success(result)
            } catch (e: Exception) {
                // Graceful degradation — never let routing failure crash the app
                Resource.Success(straightLineFallback(origin, destination))
            }
        }
    }

    /** Evicts all cache entries. */
    fun clearCache() = cache.clear()

    // ── Directions API fetch ──────────────────────────────────────────────────

    private fun fetchRoute(origin: GeoPoint, dest: GeoPoint): RouteResult {
        val url = buildDirectionsUrl(origin, dest)
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000

        val json = try {
            BufferedReader(InputStreamReader(conn.inputStream)).readText()
        } finally {
            conn.disconnect()
        }

        return parseDirectionsResponse(json, origin, dest)
    }

    private fun buildDirectionsUrl(origin: GeoPoint, dest: GeoPoint): String {
        val base = "https://maps.googleapis.com/maps/api/directions/json"
        val orig = "${origin.latitude},${origin.longitude}"
        val dst  = "${dest.latitude},${dest.longitude}"
        return "$base?origin=$orig&destination=$dst&mode=driving&key=$apiKey"
    }

    private fun parseDirectionsResponse(json: String, origin: GeoPoint, dest: GeoPoint): RouteResult {
        val root   = JSONObject(json)
        val status = root.getString("status")

        if (status != "OK") {
            return straightLineFallback(origin, dest)
        }

        val route = root.getJSONArray("routes").getJSONObject(0)
        val leg   = route.getJSONArray("legs").getJSONObject(0)

        val distanceM  = leg.getJSONObject("distance").getInt("value")
        val durationS  = leg.getJSONObject("duration").getInt("value")
        val encodedPoly = route.getJSONObject("overview_polyline").getString("points")

        return RouteResult(
            origin          = origin,
            destination     = dest,
            distanceKm      = distanceM / 1000.0,
            durationSeconds = durationS,
            polyline        = decodePolyline(encodedPoly),
            isFallback      = false
        )
    }

    // ── Straight-line fallback ────────────────────────────────────────────────

    private fun straightLineFallback(origin: GeoPoint, dest: GeoPoint): RouteResult {
        val distKm = DistanceCalculator.distanceKm(origin, dest)
        // Estimate road duration as straight-line / 25 km/h (urban delivery)
        val durationS = ((distKm / 25.0) * 3600).toInt().coerceAtLeast(60)
        return RouteResult(
            origin          = origin,
            destination     = dest,
            distanceKm      = distKm,
            durationSeconds = durationS,
            polyline        = listOf(origin.toLatLng(), dest.toLatLng()),
            isFallback      = true
        )
    }

    // ── Polyline decoder (Google encoded format) ──────────────────────────────

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dLat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dLng

            poly.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return poly
    }

    // ── Cache key helpers ─────────────────────────────────────────────────────

    /** Round to 4 decimal places so minor GPS jitter hits the same cache slot. */
    private fun Double.round4() = "%.4f".format(this)

    private fun cacheKey(origin: GeoPoint, dest: GeoPoint) = CacheKey(
        origin.latitude.round4(), origin.longitude.round4(),
        dest.latitude.round4(),   dest.longitude.round4()
    )

    private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L  // 5 minutes
    }
}
