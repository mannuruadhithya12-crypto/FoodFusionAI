package com.foodfusionai.app.ui.order

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.navigation.fragment.navArgs
import com.foodfusionai.app.R
import com.foodfusionai.app.data.location.LocationFreshness
import com.foodfusionai.app.data.location.MarkerAnimator
import com.foodfusionai.app.databinding.FragmentLiveTrackingBinding
import com.foodfusionai.app.data.models.order.OrderStatus
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Full-screen live order-tracking map screen.
 *
 * Part T  — Live customer map: restaurant, driver, customer destination markers + route
 * Part U  — Smooth driver marker animation (via [MarkerAnimator])
 * Part V  — Driver heading rotation applied to the marker
 * Part Z  — Route polyline drawn on map (real road or straight-line fallback)
 * Part AA — Driver navigation intent ("Open Navigation" button)
 * Part AO — Offline indicator: shows "Updated X ago" when GPS is stale
 *
 * Navigation: receives orderId via SafeArgs from [OrderDetailsFragment] /
 * [OrdersFragment] using the action defined in nav_graph.
 */
class LiveTrackingFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentLiveTrackingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LiveTrackingViewModel by viewModels { LiveTrackingViewModel.Factory() }
    private val args: LiveTrackingFragmentArgs by navArgs()

    // ── Map state ──────────────────────────────────────────────────────────────
    private var googleMap: GoogleMap? = null
    private var driverMarker: Marker? = null
    private var restaurantMarker: Marker? = null
    private var customerMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private var hasInitiallyFitted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapContainer) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        setupListeners()
        observeState()

        viewModel.startTracking(args.orderId)
    }

    // ── Map ready ─────────────────────────────────────────────────────────────

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = true
            isMapToolbarEnabled = false
            isMyLocationButtonEnabled = false
        }
        // Re-render any state that arrived before the map was ready
        viewModel.uiState.value.let { renderState(it) }
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnCancelOrder.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cancel Order")
                .setMessage("Are you sure you want to cancel this order?")
                .setPositiveButton("Yes, Cancel") { _, _ -> viewModel.cancelOrder() }
                .setNegativeButton("No", null)
                .show()
        }

        // Part AA: open Google Maps navigation to driver destination
        binding.btnNavigate.setOnClickListener {
            val state = viewModel.uiState.value
            val dest = state.customerLatLng
            if (dest != null) {
                openNavigation(dest)
            }
        }
    }

    // ── State observation ─────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: LiveTrackingUiState) {
        val map = googleMap // may be null before map is ready

        // Loading
        binding.progressBar.isVisible = state.isLoading

        // Offline / stale banner (Part AO)
        val showBanner = state.isOffline ||
                state.driverFreshness == LocationFreshness.STALE ||
                state.driverFreshness == LocationFreshness.OFFLINE && state.order?.orderStatus == OrderStatus.OUT_FOR_DELIVERY
        binding.cardOfflineBanner.isVisible = showBanner
        if (showBanner) {
            binding.tvOfflineBanner.text = when {
                state.isOffline -> "📵 Offline — ${state.locationAgeLabel}"
                state.driverFreshness == LocationFreshness.STALE -> "⚡ ${state.locationAgeLabel}"
                else -> "⚡ Driver location updating…"
            }
        }

        val order = state.order ?: return

        // Bottom sheet visible once we have order data
        binding.cardBottomSheet.isVisible = true

        // Status label
        binding.tvOrderStatus.text = orderStatusLabel(order.orderStatus)

        // Location age label (Part AO)
        val showAge = state.driverFreshness != LocationFreshness.HEALTHY &&
                state.locationAgeLabel.isNotBlank()
        binding.tvLocationAge.isVisible = showAge
        binding.tvLocationAge.text = state.locationAgeLabel

        // ETA
        binding.tvEta.text = state.eta.displayText

        // Route fallback notice
        binding.tvRouteFallback.isVisible = state.isRouteFallback

        // Driver info (Part T)
        val partner = order.deliveryPartner
        if (partner != null) {
            binding.layoutDriverInfo.isVisible = true
            binding.tvDriverName.text = "🚗  ${partner.name} · ${partner.vehicleNumber}"
            binding.btnNavigate.isVisible = state.customerLatLng != null
        } else {
            binding.layoutDriverInfo.isVisible = false
        }

        // Cancel button
        binding.btnCancelOrder.isVisible = state.canCancel
        binding.btnCancelOrder.isEnabled = !state.isCancelling
        binding.btnCancelOrder.text = if (state.isCancelling) "Cancelling…" else "Cancel Order"

        state.cancelError?.let { err ->
            Snackbar.make(binding.root, err, Snackbar.LENGTH_LONG).show()
            viewModel.clearErrors()
        }

        // ── Map rendering ─────────────────────────────────────────────────────
        if (map == null) return

        // Driver marker with animation (Parts U & V)
        val driverLatLng = state.driverLatLng
        if (driverLatLng != null) {
            val bearing = state.driverLocation?.heading?.takeIf { it > 0f }
            if (driverMarker == null) {
                driverMarker = map.addMarker(
                    MarkerOptions()
                        .position(driverLatLng)
                        .title("Driver")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        .flat(true) // required for rotation to work correctly
                        .anchor(0.5f, 0.5f)
                )
                bearing?.let { driverMarker?.rotation = it }
            } else {
                // Smooth animation (Part U) + heading rotation (Part V)
                MarkerAnimator.animateTo(driverMarker!!, driverLatLng, bearing)
            }
        } else if (driverMarker != null) {
            driverMarker?.isVisible = false
        }

        // Customer destination marker
        val customerLatLng = state.customerLatLng
        if (customerLatLng != null && customerMarker == null) {
            customerMarker = map.addMarker(
                MarkerOptions()
                    .position(customerLatLng)
                    .title("Your location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        // Route polyline (Part Z)
        val route = state.route
        if (route != null) {
            routePolyline?.remove()
            routePolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(route.polyline)
                    .width(8f)
                    .color(requireContext().getColor(android.R.color.holo_blue_dark))
                    .geodesic(true)
            )
        }

        // Fit camera to show driver + customer on first render
        if (!hasInitiallyFitted && driverLatLng != null && customerLatLng != null) {
            hasInitiallyFitted = true
            val bounds = LatLngBounds.builder()
                .include(driverLatLng)
                .include(customerLatLng)
                .build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        } else if (!hasInitiallyFitted && customerLatLng != null) {
            hasInitiallyFitted = true
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(customerLatLng, 14f))
        }
    }

    // ── Navigation intent (Part AA) ───────────────────────────────────────────

    private fun openNavigation(destination: LatLng) {
        val uri = Uri.parse("google.navigation:q=${destination.latitude},${destination.longitude}&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback to browser Maps
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${destination.latitude},${destination.longitude}")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun orderStatusLabel(status: OrderStatus) = when (status) {
        OrderStatus.CONFIRMED          -> "Order confirmed"
        OrderStatus.PREPARING          -> "Restaurant is preparing your order"
        OrderStatus.READY_FOR_PICKUP   -> "Order ready — driver picking up"
        OrderStatus.OUT_FOR_DELIVERY   -> "Order on the way"
        OrderStatus.DELIVERED          -> "Order delivered 🎉"
        OrderStatus.CANCELLED          -> "Order cancelled"
        OrderStatus.PENDING_PAYMENT    -> "Awaiting payment"
        OrderStatus.PAYMENT_PROCESSING -> "Processing payment"
        OrderStatus.PAYMENT_FAILED     -> "Payment failed"
        else                           -> status.name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        MarkerAnimator.cancel()
        viewModel.stopTracking()
        _binding = null
    }
}
