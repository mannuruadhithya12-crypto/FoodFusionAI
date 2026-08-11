package com.foodfusionai.app.data.location

import com.google.android.gms.maps.model.LatLng

/**
 * Result of a routing request.
 *
 * When real routing data is available, [polyline] and [durationSeconds] will
 * be populated.  When only a straight-line estimate is available (fallback),
 * [isFallback] will be true.
 */
data class RouteResult(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val distanceKm: Double,
    val durationSeconds: Int,
    /** Decoded polyline for drawing on the map; may be a 2-point straight line if [isFallback]. */
    val polyline: List<LatLng>,
    /** True when this is a Haversine straight-line estimate, not real road routing. */
    val isFallback: Boolean = false
) {
    val durationMinutes: Int get() = (durationSeconds / 60.0 + 0.5).toInt().coerceAtLeast(1)
}

/** ETA calculation states shown in the tracking UI. */
enum class EtaState {
    /** Actively calculating — show spinner. */
    CALCULATING,
    /** Real routing data is available. */
    AVAILABLE,
    /** Straight-line estimate — routing failed or unavailable; show "~" prefix. */
    APPROXIMATE,
    /** More than 5 minutes have passed since ETA was last updated. */
    STALE,
    /** Cannot determine ETA at all. */
    UNAVAILABLE
}

/** A computed ETA that carries its confidence state. */
data class EtaInfo(
    val state: EtaState,
    /** Lower bound of the ETA window in minutes. */
    val minMinutes: Int = 0,
    /** Upper bound of the ETA window in minutes. */
    val maxMinutes: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Human-readable label: "12–18 min", "~15 min", "Calculating…", "Unavailable". */
    val displayText: String
        get() = when (state) {
            EtaState.CALCULATING  -> "Calculating…"
            EtaState.AVAILABLE    -> "$minMinutes–$maxMinutes min"
            EtaState.APPROXIMATE  -> "~$minMinutes–$maxMinutes min"
            EtaState.STALE        -> "~$minMinutes–$maxMinutes min (updating…)"
            EtaState.UNAVAILABLE  -> "ETA unavailable"
        }

    companion object {
        val CALCULATING = EtaInfo(EtaState.CALCULATING)
        val UNAVAILABLE = EtaInfo(EtaState.UNAVAILABLE)
    }
}
