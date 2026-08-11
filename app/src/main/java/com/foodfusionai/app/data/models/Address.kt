package com.foodfusionai.app.data.models

/**
 * A saved delivery address for a user.
 *
 * Phase 16 additions:
 *   [latitude] / [longitude]  — real coordinates (0.0 = unknown / not yet geocoded)
 *   [geohash]                 — 9-char geohash for Firestore geo-queries
 *   [placeId]                 — Google Places ID; populated when address was picked
 *                               on the map or chosen from Places autocomplete
 *
 * Backward compatibility: existing Firestore documents without geohash/placeId
 * will deserialise to the empty-string defaults, which is safe.
 */
data class Address(
    val id: String = "",
    val userId: String = "",
    val type: String = "Home", // Home, Work, Other
    val recipientName: String = "",
    val phoneNumber: String = "",
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val landmark: String = "",
    val instructions: String = "",
    val isDefault: Boolean = false,

    // ── Phase 16: real geospatial fields ──────────────────────────────────────
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** 9-character geohash computed from lat/lon. Empty when coordinates are unknown. */
    val geohash: String = "",
    /** Google Places place_id; empty when address was entered manually. */
    val placeId: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Returns true when the address has real GPS coordinates attached. */
    val hasCoordinates: Boolean get() = latitude != 0.0 || longitude != 0.0

    /** Short display string for UI labels, e.g. "Koramangala, Bengaluru". */
    val displayLine: String get() = listOf(street, city).filter { it.isNotBlank() }.joinToString(", ")
}
