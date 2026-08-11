package com.foodfusionai.app.data.location

/**
 * Geohash encoder used throughout Phase 16 for Firestore geo-queries.
 *
 * A geohash encodes a lat/lon pair into a compact alphanumeric string.
 * Longer strings → higher precision.  Shorter strings cover larger areas.
 *
 * Precision guide (approximate cell size):
 *   4 chars → ~39 km × 20 km
 *   5 chars → ~4.9 km × 4.9 km  ← used for delivery-zone area queries
 *   6 chars → ~1.2 km × 0.6 km  ← default precision stored on addresses
 *   9 chars → ~4.8 m × 4.8 m    ← max precision used for driver location
 *
 * Firestore geo-query strategy: store geohash on every document that has
 * lat/lon.  To find documents within radius R, compute the set of geohash
 * prefixes that cover the bounding box, then issue a bounded range query
 * (geohash >= prefix, geohash <= prefix + "\uf8ff") for each prefix and
 * post-filter the results by exact distance.
 *
 * This is the same strategy used by the official geofire-common library
 * but without a runtime dependency.
 */
object GeoHashUtil {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private const val DEFAULT_PRECISION = 9

    /**
     * Encodes [lat]/[lon] to a geohash string of [precision] characters.
     */
    fun encode(lat: Double, lon: Double, precision: Int = DEFAULT_PRECISION): String {
        require(precision in 1..12) { "Geohash precision must be 1..12" }
        require(lat in -90.0..90.0) { "Latitude out of range: $lat" }
        require(lon in -180.0..180.0) { "Longitude out of range: $lon" }

        var minLat = -90.0;  var maxLat = 90.0
        var minLon = -180.0; var maxLon = 180.0

        val hash = StringBuilder()
        var bits = 0
        var bitsTotal = 0
        var hashValue = 0
        var isLon = true

        while (hash.length < precision) {
            val mid: Double
            if (isLon) {
                mid = (minLon + maxLon) / 2
                if (lon >= mid) { hashValue = (hashValue shl 1) or 1; minLon = mid }
                else           { hashValue = hashValue shl 1;          maxLon = mid }
            } else {
                mid = (minLat + maxLat) / 2
                if (lat >= mid) { hashValue = (hashValue shl 1) or 1; minLat = mid }
                else            { hashValue = hashValue shl 1;         maxLat = mid }
            }
            isLon = !isLon
            bits++
            bitsTotal++

            if (bits == 5) {
                hash.append(BASE32[hashValue])
                hashValue = 0
                bits = 0
            }
        }
        return hash.toString()
    }

    /**
     * Returns the set of geohash prefixes (at [precision] chars) that cover
     * a circle of radius [radiusKm] around [center].
     *
     * Firestore queries: for each prefix P, query
     *   geohash >= P  AND  geohash <= P + "\uf8ff"
     * then post-filter by exact distance.
     */
    fun queryPrefixes(center: GeoPoint, radiusKm: Double, precision: Int = 5): List<String> {
        // Compute bounding box
        val latDelta = radiusKm / 110.574          // ~111 km per degree latitude
        val lonDelta = radiusKm / (111.320 * Math.cos(Math.toRadians(center.latitude)))

        val minLat = (center.latitude - latDelta).coerceAtLeast(-90.0)
        val maxLat = (center.latitude + latDelta).coerceAtMost(90.0)
        val minLon = (center.longitude - lonDelta).coerceAtLeast(-180.0)
        val maxLon = (center.longitude + lonDelta).coerceAtMost(180.0)

        // Sample the 9-point grid of the bounding box corners, edges, and center.
        val samples = listOf(
            center,
            GeoPoint(minLat, minLon),
            GeoPoint(minLat, center.longitude),
            GeoPoint(minLat, maxLon),
            GeoPoint(center.latitude, minLon),
            GeoPoint(center.latitude, maxLon),
            GeoPoint(maxLat, minLon),
            GeoPoint(maxLat, center.longitude),
            GeoPoint(maxLat, maxLon)
        )

        return samples
            .map { encode(it.latitude, it.longitude, precision) }
            .distinct()
            .sorted()
    }

    /** Convenience: encode a [GeoPoint] to default precision. */
    fun encode(point: GeoPoint, precision: Int = DEFAULT_PRECISION): String =
        encode(point.latitude, point.longitude, precision)

    /** Returns the Firestore range pair [start, end] for a given prefix. */
    fun rangeForPrefix(prefix: String): Pair<String, String> =
        prefix to (prefix + "\uf8ff")
}
