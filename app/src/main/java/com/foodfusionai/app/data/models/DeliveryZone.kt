package com.foodfusionai.app.data.models

/**
 * A geographic delivery zone.
 *
 * Zones allow different delivery fees, minimum order amounts, and service areas
 * without hardcoding values into the Android app.  All values here are read from
 * Firestore (`deliveryZones/{zoneId}`) and are informational on the client side —
 * the authoritative fee calculation happens server-side in the `calculateDeliveryFee`
 * Cloud Function.
 *
 * Firestore collection: `deliveryZones`
 */
data class DeliveryZone(
    val zoneId: String = "",
    val name: String = "",
    /** Zone centre latitude. */
    val centerLat: Double = 0.0,
    /** Zone centre longitude. */
    val centerLon: Double = 0.0,
    /** Radius in km.  A customer must fall within this radius to use the zone. */
    val radiusKm: Double = 0.0,
    /** Zone-wide delivery fee in INR.  0 = free delivery. */
    val deliveryFee: Double = 0.0,
    /** Minimum cart subtotal (INR) required for delivery in this zone. */
    val minimumOrderAmount: Double = 0.0,
    /** Hard cap on delivery distance from restaurant within this zone. */
    val maximumDeliveryDistanceKm: Double = 20.0,
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * Result returned by the `validateDeliveryLocation` Cloud Function.
 *
 * The Android app calls this function at checkout time to confirm the
 * customer's address is within the restaurant's delivery range, and to
 * obtain the server-authoritative delivery fee.
 */
data class DeliveryValidationResult(
    /** True = delivery can be made; false = address is outside range. */
    val isDeliverable: Boolean = false,
    /** Distance in km from restaurant to customer (Haversine, computed server-side). */
    val distanceKm: Double = 0.0,
    /** Server-authoritative delivery fee in INR. */
    val deliveryFee: Double = 0.0,
    /** Human-readable reason when [isDeliverable] is false. */
    val reason: String = "",
    /** Matched zone ID if the customer falls within a zone; empty otherwise. */
    val zoneId: String = ""
)
