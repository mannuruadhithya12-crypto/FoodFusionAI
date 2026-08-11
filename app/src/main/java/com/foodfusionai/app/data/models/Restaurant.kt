package com.foodfusionai.app.data.models

/**
 * Restaurant data model.
 *
 * Phase 16 additions:
 *   [latitude] / [longitude]  — restaurant GPS location
 *   [geohash]                 — 9-char geohash for Firestore geo-queries
 *   [deliveryRadiusKm]        — configurable delivery radius in kilometres (default 8 km)
 *   [deliveryZoneId]          — optional zone assignment for zone-based fee logic
 *
 * Backward compat: existing documents without new fields deserialise to sensible defaults.
 */
data class Restaurant(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val rating: Double = 0.0,
    val deliveryTime: String = "",
    val deliveryFee: Double = 0.0,
    val address: String = "",
    val isOpen: Boolean = false,
    val categories: List<String> = emptyList(),

    // ── Phase 16: geospatial ─────────────────────────────────────────────────
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** 9-char geohash; empty when coordinates unknown. */
    val geohash: String = "",
    /** Delivery radius in km; 0 means "use default (8 km)". */
    val deliveryRadiusKm: Double = 8.0,
    /** Delivery zone ID this restaurant belongs to; empty = no zone assigned. */
    val deliveryZoneId: String = ""
) {
    /** Returns true when the restaurant has real GPS coordinates. */
    val hasCoordinates: Boolean get() = latitude != 0.0 || longitude != 0.0

    /** Effective delivery radius; falls back to 8 km when not explicitly set. */
    val effectiveDeliveryRadiusKm: Double
        get() = if (deliveryRadiusKm > 0.0) deliveryRadiusKm else 8.0
}
