package com.foodfusionai.app.ui.location

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.location.GeoPoint
import com.foodfusionai.app.data.location.GeocodingService
import com.foodfusionai.app.data.location.LocationFreshness
import com.foodfusionai.app.data.location.LocationPermissionState
import com.foodfusionai.app.data.location.LocationProvider
import com.foodfusionai.app.data.location.LocationUnavailableException
import com.foodfusionai.app.data.location.ResolvedAddress
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages the customer's current location state.
 *
 * Consumed by:
 *   - [HomeFragment]        — "Delivering to Koramangala, Bengaluru" header
 *   - [MapPickerFragment]   — blue-dot position on the map
 *   - [CheckoutFragment]    — auto-selects nearest default address
 *
 * Permission flow: the ViewModel does NOT trigger permission requests itself.
 * The Fragment calls [onPermissionResult] after the system dialog resolves.
 * This keeps permission logic in the View layer where the Activity context lives.
 */
class CustomerLocationViewModel(
    private val locationProvider: LocationProvider,
    private val geocodingService: GeocodingService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerLocationUiState())
    val uiState: StateFlow<CustomerLocationUiState> = _uiState.asStateFlow()

    // ── Permission result handler ─────────────────────────────────────────────

    /**
     * Called by the Fragment when the system permission dialog resolves.
     * Automatically fetches location when [state] is [LocationPermissionState.Granted].
     */
    fun onPermissionResult(state: LocationPermissionState) {
        _uiState.update { it.copy(permissionState = state) }
        when (state) {
            is LocationPermissionState.Granted -> fetchCurrentLocation()
            is LocationPermissionState.DeniedPermanently ->
                _uiState.update { it.copy(userMessage = "Location permission denied. Open Settings to enable it.") }
            is LocationPermissionState.Denied ->
                _uiState.update { it.copy(userMessage = "Location permission is required to find restaurants near you.") }
            is LocationPermissionState.LocationDisabled ->
                _uiState.update { it.copy(userMessage = "Please enable location services to use this feature.") }
            else -> Unit
        }
    }

    /** Call when the user actively taps "Use my location". */
    fun requestCurrentLocation() {
        _uiState.update { it.copy(isLocating = true, userMessage = null) }
    }

    // ── GPS fetch ─────────────────────────────────────────────────────────────

    fun fetchCurrentLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLocating = true, locationError = null) }

            if (!locationProvider.isLocationEnabled()) {
                _uiState.update {
                    it.copy(
                        isLocating = false,
                        permissionState = LocationPermissionState.LocationDisabled,
                        userMessage = "Please enable location services."
                    )
                }
                return@launch
            }

            try {
                val result = locationProvider.getCurrentLocation()
                _uiState.update {
                    it.copy(
                        currentPoint = result.point,
                        lastUpdatedAt = result.timestamp,
                        isLocating = false
                    )
                }
                // Reverse geocode on IO thread
                reverseGeocodeCurrentPoint(result.point)
            } catch (e: LocationUnavailableException) {
                _uiState.update {
                    it.copy(
                        isLocating = false,
                        locationError = "Location unavailable. Please check GPS settings."
                    )
                }
            } catch (e: SecurityException) {
                _uiState.update {
                    it.copy(
                        isLocating = false,
                        permissionState = LocationPermissionState.DeniedPermanently,
                        locationError = "Location permission missing."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLocating = false,
                        locationError = "Could not get location: ${e.message}"
                    )
                }
            }
        }
    }

    // ── Allow manual address override ─────────────────────────────────────────

    /** Called when the user manually confirms an address from the map picker. */
    fun setManualLocation(point: GeoPoint, resolved: ResolvedAddress) {
        _uiState.update {
            it.copy(
                currentPoint = point,
                resolvedAddress = resolved,
                lastUpdatedAt = System.currentTimeMillis(),
                isLocating = false
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null, locationError = null) }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun reverseGeocodeCurrentPoint(point: GeoPoint) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = geocodingService.reverseGeocode(point)) {
                is Resource.Success -> _uiState.update { it.copy(resolvedAddress = result.data) }
                else -> Unit // Non-fatal — display coordinates if geocoding fails
            }
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CustomerLocationViewModel::class.java)) {
                return CustomerLocationViewModel(
                    LocationProvider(context),
                    GeocodingService(context)
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class CustomerLocationUiState(
    val permissionState: LocationPermissionState = LocationPermissionState.NotRequested,
    val isLocating: Boolean = false,
    val currentPoint: GeoPoint? = null,
    val resolvedAddress: ResolvedAddress? = null,
    val lastUpdatedAt: Long? = null,
    val locationError: String? = null,
    val userMessage: String? = null
) {
    /** Display label for the home screen header. */
    val deliveryLabel: String
        get() = resolvedAddress?.displayLabel
            ?: if (currentPoint != null) "Locating…" else "Select location"

    /** Freshness of the stored location fix. */
    val freshness: LocationFreshness
        get() = LocationFreshness.classify(lastUpdatedAt)
}
