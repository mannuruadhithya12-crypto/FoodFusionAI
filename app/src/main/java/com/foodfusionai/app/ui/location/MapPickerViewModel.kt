package com.foodfusionai.app.ui.location

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.location.GeoHashUtil
import com.foodfusionai.app.data.location.GeoPoint
import com.foodfusionai.app.data.location.GeocodingService
import com.foodfusionai.app.data.location.LocationProvider
import com.foodfusionai.app.data.location.LocationUnavailableException
import com.foodfusionai.app.data.location.ResolvedAddress
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for [MapPickerFragment].
 *
 * Flow:
 *  1. Fragment opens → [initialise] moves camera to current GPS position.
 *  2. User drags map → [onMapCentreChanged] debounces and reverse-geocodes.
 *  3. User taps "Confirm" → [confirmSelection] stores the chosen [GeoPoint] +
 *     [ResolvedAddress] so [AddEditAddressFragment] can read [confirmedPoint].
 *
 * Shared between fragments via the Activity's ViewModelStore (scoped to Activity).
 */
class MapPickerViewModel(
    private val locationProvider: LocationProvider,
    private val geocodingService: GeocodingService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapPickerUiState())
    val uiState: StateFlow<MapPickerUiState> = _uiState.asStateFlow()

    /** Set by [confirmSelection]; read by the address editor after navigating back. */
    private val _confirmedPoint = MutableStateFlow<GeoPoint?>(null)
    val confirmedPoint: StateFlow<GeoPoint?> = _confirmedPoint.asStateFlow()

    private val _confirmedAddress = MutableStateFlow<ResolvedAddress?>(null)
    val confirmedAddress: StateFlow<ResolvedAddress?> = _confirmedAddress.asStateFlow()

    private var geocodeJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called when the map finishes initialising — fetch GPS to set initial camera. */
    fun fetchCurrentLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!locationProvider.isLocationEnabled()) return@launch
            try {
                val result = locationProvider.getCurrentLocation()
                _uiState.update { it.copy(mapCentre = result.point) }
                reverseGeocode(result.point)
            } catch (_: LocationUnavailableException) {
            } catch (_: SecurityException) {
            }
        }
    }

    /**
     * Called every time the map camera stops.  Debounces for 400 ms to avoid
     * spamming geocoding requests while the user is still dragging.
     */
    fun onMapCentreChanged(point: GeoPoint) {
        _uiState.update { it.copy(mapCentre = point, isGeocoding = true, resolvedAddress = null) }
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            reverseGeocode(point)
        }
    }

    /** Forward-geocode a typed address and move the map to the result. */
    fun searchAddress(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isGeocoding = true) }
            when (val result = geocodingService.geocode(query)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(mapCentre = result.data, isGeocoding = false) }
                    reverseGeocode(result.data)
                }
                else -> _uiState.update { it.copy(isGeocoding = false) }
            }
        }
    }

    /**
     * Stores the confirmed selection so [AddEditAddressFragment] can read it.
     * The geohash is computed here so the address editor doesn't need to know
     * about GeoHashUtil.
     */
    fun confirmSelection() {
        val state = _uiState.value
        val point = state.mapCentre ?: return
        _confirmedPoint.value = point
        _confirmedAddress.value = state.resolvedAddress
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Reset after the address editor has consumed the confirmed values. */
    fun clearConfirmation() {
        _confirmedPoint.value = null
        _confirmedAddress.value = null
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun reverseGeocode(point: GeoPoint) {
        when (val result = geocodingService.reverseGeocode(point)) {
            is Resource.Success -> _uiState.update {
                it.copy(resolvedAddress = result.data, isGeocoding = false, error = null)
            }
            is Resource.Error -> _uiState.update {
                it.copy(
                    isGeocoding = false,
                    error = "Could not find address for this location"
                )
            }
            else -> _uiState.update { it.copy(isGeocoding = false) }
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MapPickerViewModel::class.java)) {
                return MapPickerViewModel(
                    LocationProvider(context),
                    GeocodingService(context)
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class MapPickerUiState(
    val mapCentre: GeoPoint? = null,
    val resolvedAddress: ResolvedAddress? = null,
    val isGeocoding: Boolean = false,
    val error: String? = null
)
