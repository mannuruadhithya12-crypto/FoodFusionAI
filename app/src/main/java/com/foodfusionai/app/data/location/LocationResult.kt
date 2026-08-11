package com.foodfusionai.app.data.location

/**
 * Result of a one-shot or streaming location request.
 *
 * [accuracy] is in metres — lower is better.
 * [heading]  is in degrees [0, 360) from North; null when stationary or unavailable.
 * [speed]    is in m/s; null when unavailable.
 */
data class LocationResult(
    val point: GeoPoint,
    val accuracy: Float,           // metres
    val heading: Float? = null,    // degrees, null if unavailable
    val speed: Float? = null,      // m/s, null if unavailable
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Classify accuracy for UI display. */
    val accuracyLevel: AccuracyLevel
        get() = when {
            accuracy <= 20f  -> AccuracyLevel.HIGH
            accuracy <= 75f  -> AccuracyLevel.MEDIUM
            else             -> AccuracyLevel.LOW
        }
}

enum class AccuracyLevel { HIGH, MEDIUM, LOW }
