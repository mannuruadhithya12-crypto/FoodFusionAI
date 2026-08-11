package com.foodfusionai.app.data.location

import kotlin.math.*

/**
 * Haversine-based distance calculations.
 *
 * Used wherever a quick on-device distance estimate is needed (e.g. sorting nearby
 * restaurants, estimating straight-line ETA when routing is unavailable).
 * All heavy server-side delivery-radius validation uses the Cloud Function, not this class.
 */
object DistanceCalculator {

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Returns the great-circle distance between two points in **kilometres**.
     *
     * The Haversine formula has ~0.5% error for short distances — more than
     * sufficient for display purposes.
     */
    fun distanceKm(from: GeoPoint, to: GeoPoint): Double {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(from.latitude)) *
                cos(Math.toRadians(to.latitude)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Returns distance in metres.
     */
    fun distanceMetres(from: GeoPoint, to: GeoPoint): Double =
        distanceKm(from, to) * 1000.0

    /**
     * Returns a human-readable distance string.
     *
     * Examples: "350 m", "2.4 km", "12 km"
     */
    fun formatDistance(from: GeoPoint, to: GeoPoint): String {
        val metres = distanceMetres(from, to)
        return when {
            metres < 1000 -> "${metres.toInt()} m"
            metres < 10_000 -> "%.1f km".format(metres / 1000.0)
            else -> "${(metres / 1000).toInt()} km"
        }
    }

    /**
     * Returns the bearing (heading) from [from] to [to] in degrees [0, 360).
     *
     * 0° = North, 90° = East, 180° = South, 270° = West.
     */
    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Float {
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }

    /**
     * Rough straight-line ETA in minutes given distance and average speed.
     *
     * @param distanceKm great-circle distance
     * @param avgSpeedKmh average speed; defaults to 25 km/h (urban two-wheeler)
     */
    fun straightLineEtaMinutes(distanceKm: Double, avgSpeedKmh: Double = 25.0): Int {
        if (avgSpeedKmh <= 0.0) return Int.MAX_VALUE
        return ((distanceKm / avgSpeedKmh) * 60).roundToInt().coerceAtLeast(1)
    }

    private fun Double.roundToInt(): Int = (this + 0.5).toInt()
}
