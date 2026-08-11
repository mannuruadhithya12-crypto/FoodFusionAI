package com.foodfusionai.app.ui.order

import com.foodfusionai.app.data.location.EtaInfo
import com.foodfusionai.app.data.location.LocationFreshness
import com.foodfusionai.app.data.location.RouteResult
import com.foodfusionai.app.data.models.DriverLocation
import com.foodfusionai.app.data.models.order.Order
import com.foodfusionai.app.data.models.order.OrderStatus
import com.google.android.gms.maps.model.LatLng

/**
 * Complete UI state for the live order-tracking screen.
 *
 * Separating tracking state from [OrderTrackingUiState] keeps the concerns clean:
 * [OrderTrackingUiState] owns order lifecycle; [LiveTrackingUiState] owns the map/GPS/ETA concerns.
 */
data class LiveTrackingUiState(
    // ── Order ──────────────────────────────────────────────────────────────────
    val order: Order? = null,
    val isLoading: Boolean = true,
    val error: String? = null,

    // ── Driver GPS ─────────────────────────────────────────────────────────────
    val driverLocation: DriverLocation? = null,
    val driverFreshness: LocationFreshness = LocationFreshness.OFFLINE,
    val locationAgeLabel: String = "Location unavailable",

    // ── Route ──────────────────────────────────────────────────────────────────
    val route: RouteResult? = null,
    val isRouteFallback: Boolean = false,

    // ── ETA ────────────────────────────────────────────────────────────────────
    val eta: EtaInfo = EtaInfo.UNAVAILABLE,

    // ── Offline mode (Part AO) ─────────────────────────────────────────────────
    /** True when Firestore snapshot came from cache, not server. */
    val isOffline: Boolean = false,

    // ── Cancellation ──────────────────────────────────────────────────────────
    val isCancelling: Boolean = false,
    val cancelError: String? = null
) {
    val canCancel: Boolean
        get() {
            val status = order?.orderStatus ?: return false
            return status in listOf(
                OrderStatus.CONFIRMED,
                OrderStatus.PREPARING,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAYMENT_PROCESSING,
                OrderStatus.PAYMENT_FAILED
            )
        }

    /** True when we should show the live-map card (driver assigned + GPS active). */
    val showLiveMap: Boolean
        get() = order?.deliveryPartner != null &&
                order.orderStatus == OrderStatus.OUT_FOR_DELIVERY &&
                driverFreshness != LocationFreshness.OFFLINE

    /** Current driver position as LatLng for Google Maps. */
    val driverLatLng: LatLng?
        get() = driverLocation?.let {
            if (it.latitude != 0.0 || it.longitude != 0.0)
                LatLng(it.latitude, it.longitude) else null
        }

    /** Restaurant position from the order's delivery location. */
    val restaurantLatLng: LatLng?
        get() = null // Populated when restaurant coordinates are available

    /** Customer destination from AddressSnapshot. */
    val customerLatLng: LatLng?
        get() = order?.addressSnapshot?.let {
            if (it.latitude != 0.0 || it.longitude != 0.0)
                LatLng(it.latitude, it.longitude) else null
        }
}
