package com.foodfusionai.app.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.location.EtaInfo
import com.foodfusionai.app.data.location.EtaState
import com.foodfusionai.app.data.location.GeoPoint
import com.foodfusionai.app.data.location.LocationFreshness
import com.foodfusionai.app.data.location.RouteResult
import com.foodfusionai.app.data.location.RoutingService
import com.foodfusionai.app.data.models.order.OrderStatus
import com.foodfusionai.app.data.repository.DriverLocationRepository
import com.foodfusionai.app.data.repository.OrderRepository
import com.foodfusionai.app.data.repository.OrderRepositoryImpl
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for the live order-tracking screen.
 *
 * Manages three concurrent data streams:
 *   1. Order state (from Firestore via [OrderRepository])
 *   2. Driver GPS (from Firestore `deliveryLocations/{orderId}`)
 *   3. Route + ETA (from [RoutingService], with 5-min cache)
 *
 * ETA calculation (Part X):
 *   - Uses real routing data when available (Directions API)
 *   - Falls back to Haversine straight-line estimate
 *   - Accounts for restaurant prep time + driver travel time
 *   - Adds ±20% window to avoid false precision
 *
 * Offline mode (Part AO):
 *   - Firestore offline cache keeps last-known order state visible
 *   - [LiveTrackingUiState.isOffline] + [LiveTrackingUiState.locationAgeLabel]
 *     are surfaced to the UI
 *
 * ETA freshness (Part Y): ETA is marked STALE when > 5 min since last GPS update.
 */
class LiveTrackingViewModel(
    private val orderRepository: OrderRepository,
    private val driverLocationRepository: DriverLocationRepository,
    private val routingService: RoutingService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTrackingUiState())
    val uiState: StateFlow<LiveTrackingUiState> = _uiState.asStateFlow()

    private var orderJob: Job? = null
    private var driverJob: Job? = null
    private var routeJob: Job? = null
    private var currentOrderId: String? = null

    // ── Public API ─────────────────────────────────────────────────────────────

    fun startTracking(orderId: String) {
        if (currentOrderId == orderId) return
        currentOrderId = orderId
        stopAllJobs()

        observeOrder(orderId)
        observeDriverLocation(orderId)
    }

    fun stopTracking() {
        stopAllJobs()
        currentOrderId = null
    }

    fun cancelOrder() {
        val orderId = currentOrderId ?: return
        if (!_uiState.value.canCancel) return

        _uiState.update { it.copy(isCancelling = true, cancelError = null) }
        viewModelScope.launch {
            try {
                val data = hashMapOf("orderId" to orderId, "cancelReason" to "Cancelled by user")
                com.google.firebase.functions.FirebaseFunctions.getInstance()
                    .getHttpsCallable("cancelOrder").call(data)
                    .await()
                _uiState.update { it.copy(isCancelling = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCancelling = false, cancelError = e.message ?: "Cancellation failed") }
            }
        }
    }

    fun clearErrors() {
        _uiState.update { it.copy(error = null, cancelError = null) }
    }

    // ── Order observation ──────────────────────────────────────────────────────

    private fun observeOrder(orderId: String) {
        orderJob = viewModelScope.launch {
            orderRepository.observeOrderById(orderId).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = it.order == null) }
                    is Resource.Success -> {
                        val order = resource.data
                        _uiState.update { it.copy(isLoading = false, order = order, error = null) }

                        // Stop driver observation when delivery is terminal
                        if (order?.orderStatus in listOf(OrderStatus.DELIVERED, OrderStatus.CANCELLED)) {
                            driverJob?.cancel()
                            _uiState.update { it.copy(driverLocation = null, driverFreshness = LocationFreshness.OFFLINE) }
                        }

                        // Refresh route when order or driver position changes
                        refreshRouteIfNeeded()
                    }
                    is Resource.Error -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOffline = it.order != null, // cached data available
                            error = if (it.order == null) resource.message else null
                        )
                    }
                    is Resource.Empty -> _uiState.update { it.copy(isLoading = false, error = "Order not found") }
                }
            }
        }
    }

    // ── Driver location observation ────────────────────────────────────────────

    private fun observeDriverLocation(orderId: String) {
        driverJob = viewModelScope.launch {
            driverLocationRepository.observeDriverLocation(orderId).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val dl = resource.data
                        val freshness = LocationFreshness.classify(dl.updatedAt)
                        _uiState.update {
                            it.copy(
                                driverLocation = dl,
                                driverFreshness = freshness,
                                locationAgeLabel = LocationFreshness.ageLabel(dl.updatedAt)
                            )
                        }
                        refreshRouteIfNeeded()
                    }
                    is Resource.Empty -> {
                        // Tracking deactivated (delivery complete)
                        _uiState.update {
                            it.copy(driverLocation = null, driverFreshness = LocationFreshness.OFFLINE)
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(driverFreshness = LocationFreshness.OFFLINE) }
                    }
                    else -> Unit
                }
            }
        }
    }

    // ── Route + ETA (Parts W, X, Y) ────────────────────────────────────────────

    private fun refreshRouteIfNeeded() {
        val state = _uiState.value
        val order = state.order ?: return

        // Only compute route when driver is active and we have destination coords
        val driverLoc = state.driverLocation
        val destLat = order.addressSnapshot?.latitude ?: 0.0
        val destLon = order.addressSnapshot?.longitude ?: 0.0
        if (destLat == 0.0 && destLon == 0.0) {
            computeApproximateEta(order)
            return
        }

        if (driverLoc == null || !driverLoc.isActive) {
            computeApproximateEta(order)
            return
        }

        // Cancel in-flight route request
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            _uiState.update { it.copy(eta = EtaInfo.CALCULATING) }

            val from = GeoPoint(driverLoc.latitude, driverLoc.longitude)
            val to   = GeoPoint(destLat, destLon)

            val result = routingService.getRoute(from, to)
            val route = (result as? Resource.Success)?.data

            if (route != null) {
                val eta = buildEta(
                    route           = route,
                    prepRemaining   = estimatePrepRemaining(order),
                    isFallback      = route.isFallback,
                    driverUpdatedAt = driverLoc.updatedAt
                )
                _uiState.update { it.copy(route = route, isRouteFallback = route.isFallback, eta = eta) }
            } else {
                computeApproximateEta(order)
            }
        }
    }

    /**
     * Builds an [EtaInfo] from a [RouteResult].
     *
     * ETA window = prep time remaining + routing duration + ±20% buffer.
     * State is APPROXIMATE when route is a straight-line fallback, or when
     * the driver GPS fix is more than 5 minutes stale.
     */
    private fun buildEta(
        route: RouteResult,
        prepRemaining: Int,
        isFallback: Boolean,
        driverUpdatedAt: Long
    ): EtaInfo {
        val freshness = LocationFreshness.classify(driverUpdatedAt)
        val baseMinutes = route.durationMinutes + prepRemaining

        val state = when {
            isFallback || freshness == LocationFreshness.STALE  -> EtaState.APPROXIMATE
            freshness == LocationFreshness.OFFLINE              -> EtaState.STALE
            else                                                -> EtaState.AVAILABLE
        }

        val buffer = maxOf(1, (baseMinutes * 0.20).toInt())
        return EtaInfo(
            state      = state,
            minMinutes = maxOf(1, baseMinutes - buffer),
            maxMinutes = baseMinutes + buffer,
            updatedAt  = System.currentTimeMillis()
        )
    }

    /** Fallback ETA based on estimated delivery time field from the order. */
    private fun computeApproximateEta(order: com.foodfusionai.app.data.models.order.Order) {
        val estimatedAt = order.estimatedDeliveryAt
        if (estimatedAt != null && estimatedAt > System.currentTimeMillis()) {
            val remainingMs = estimatedAt - System.currentTimeMillis()
            val mins = (remainingMs / 60_000L).toInt().coerceAtLeast(1)
            val buffer = maxOf(1, (mins * 0.20).toInt())
            _uiState.update {
                it.copy(
                    eta = EtaInfo(
                        state      = EtaState.APPROXIMATE,
                        minMinutes = maxOf(1, mins - buffer),
                        maxMinutes = mins + buffer
                    )
                )
            }
        } else {
            _uiState.update { it.copy(eta = EtaInfo.UNAVAILABLE) }
        }
    }

    /** Rough remaining prep time in minutes based on order status. */
    private fun estimatePrepRemaining(order: com.foodfusionai.app.data.models.order.Order): Int =
        when (order.orderStatus) {
            OrderStatus.CONFIRMED     -> 20
            OrderStatus.PREPARING     -> 10
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.OUT_FOR_DELIVERY -> 0
            else                      -> 0
        }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun stopAllJobs() {
        orderJob?.cancel()
        driverJob?.cancel()
        routeJob?.cancel()
    }

    // ── Factory ────────────────────────────────────────────────────────────────

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LiveTrackingViewModel::class.java)) {
                // BuildConfig.MAPS_API_KEY is injected by the Secrets Gradle Plugin from
                // local.properties.  Falls back to "" (Haversine-only mode) when the
                // placeholder key from secrets.defaults.properties is present, so the
                // app still runs without a real Maps key — routing just shows a straight
                // line estimate with the APPROXIMATE ETA state.
                val apiKey = try {
                    val key = com.foodfusionai.app.BuildConfig.MAPS_API_KEY
                    if (key == "YOUR_MAPS_API_KEY_HERE") "" else key
                } catch (_: Exception) { "" }

                return LiveTrackingViewModel(
                    orderRepository          = OrderRepositoryImpl(),
                    driverLocationRepository = DriverLocationRepository(),
                    routingService           = RoutingService(apiKey = apiKey)
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
