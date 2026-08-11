package com.foodfusionai.app.data.location

/**
 * Immutable geographic coordinate pair.
 *
 * All latitude/longitude values across Phase 16 flow through this type so
 * the rest of the app never deals with bare Double pairs.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    /** Returns true when the coordinate is a real fix, not a 0/0 default. */
    val isValid: Boolean
        get() = latitude != 0.0 || longitude != 0.0

    /** Returns true when values are within legal geographic ranges. */
    val isInBounds: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0

    override fun toString(): String = "(%.6f, %.6f)".format(latitude, longitude)

    companion object {
        val INVALID = GeoPoint(0.0, 0.0)
    }
}
