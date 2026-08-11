package com.foodfusionai.app.ui.location

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentMapPickerBinding
import com.foodfusionai.app.data.location.GeoPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Full-screen map picker where the user can:
 *
 *  1. See their current GPS location as the initial camera position.
 *  2. Drag the map to move the centre pin over the desired delivery point.
 *  3. Watch the address resolve in real-time via reverse geocoding as the pin moves.
 *  4. Tap a Places-style autocomplete chip to search by address/landmark.
 *  5. Confirm the selection — result is passed back to [AddEditAddressFragment]
 *     via the shared [MapPickerViewModel].
 *
 * Navigation:
 *   Caller navigates to `mapPickerFragment` (add to nav_graph).
 *   On confirm, navigates back and the calling screen reads [MapPickerViewModel.confirmedPoint].
 *
 * Part F — Map picker with reverse geocode + confirm.
 * Part H — Places autocomplete field.
 */
class MapPickerFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapPickerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapPickerViewModel by viewModels {
        MapPickerViewModel.Factory(requireContext())
    }

    private var googleMap: GoogleMap? = null

    // Flag to suppress camera-idle callbacks while we're programmatically moving the camera
    private var isProgrammaticMove = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialise the embedded map fragment
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapContainer) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        setupListeners()
        observeState()
    }

    // ── GoogleMap.OnMapReadyCallback ──────────────────────────────────────────

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isMyLocationButtonEnabled = false
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }

        // Disable default map toolbar to avoid leaking place details UI
        map.uiSettings.isMapToolbarEnabled = false

        // When the camera stops moving, reverse-geocode the new centre
        map.setOnCameraIdleListener {
            if (!isProgrammaticMove) {
                val centre = map.cameraPosition.target
                viewModel.onMapCentreChanged(GeoPoint(centre.latitude, centre.longitude))
            }
            isProgrammaticMove = false
        }

        // Move to initial position (current GPS or India centre as fallback)
        val initialPoint = viewModel.uiState.value.mapCentre
            ?: GeoPoint(20.5937, 78.9629) // India centre
        moveCameraTo(initialPoint, animate = false)

        // Show user's blue dot if permission is granted
        try {
            map.isMyLocationEnabled = true
        } catch (_: SecurityException) { /* permission not granted */ }
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // "Confirm location" button
        binding.btnConfirmLocation.setOnClickListener {
            viewModel.confirmSelection()
            findNavController().navigateUp()
        }

        // Places-style search field — basic autocomplete via Geocoder
        binding.etSearchAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: return
                if (query.length >= 3) viewModel.searchAddress(query)
            }
        })

        // My-location FAB: moves camera back to current GPS position
        binding.fabMyLocation.setOnClickListener {
            viewModel.fetchCurrentLocation()
        }
    }

    // ── State observation ─────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: MapPickerUiState) {
        // Address label below the pin
        binding.tvResolvedAddress.text = when {
            state.isGeocoding -> "Finding address…"
            state.resolvedAddress != null -> state.resolvedAddress.fullAddress
            else -> "Move the map to select a location"
        }

        // Confirm button enabled only when we have a resolved address
        binding.btnConfirmLocation.isEnabled = state.resolvedAddress != null && !state.isGeocoding

        // Loading indicator
        binding.progressGeocoding.isVisible = state.isGeocoding

        // Move camera when location fetch completes or search resolves
        state.mapCentre?.let { point ->
            val currentCentre = googleMap?.cameraPosition?.target
            val isSame = currentCentre != null &&
                    Math.abs(currentCentre.latitude - point.latitude) < 0.0001 &&
                    Math.abs(currentCentre.longitude - point.longitude) < 0.0001
            if (!isSame) moveCameraTo(point, animate = true)
        }

        // Error snackbar
        state.error?.let { err ->
            Snackbar.make(binding.root, err, Snackbar.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    private fun moveCameraTo(point: GeoPoint, animate: Boolean) {
        val map = googleMap ?: return
        isProgrammaticMove = true
        val latLng = LatLng(point.latitude, point.longitude)
        val update = CameraUpdateFactory.newLatLngZoom(latLng, 16f)
        if (animate) map.animateCamera(update) else map.moveCamera(update)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
