package com.foodfusionai.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentHomeBinding
import com.foodfusionai.app.data.location.LocationPermissionState
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.ui.home.adapters.BannerAdapter
import com.foodfusionai.app.ui.home.adapters.CategoryAdapter
import com.foodfusionai.app.ui.home.adapters.FoodAdapter
import com.foodfusionai.app.ui.home.adapters.RestaurantAdapter
import com.foodfusionai.app.ui.location.CustomerLocationViewModel
import com.foodfusionai.app.data.location.LocationPermissionHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.foodfusionai.app.utils.hide
import com.foodfusionai.app.utils.show
import com.foodfusionai.app.utils.showToast
import kotlinx.coroutines.launch

/**
 * Fragment rendering the Home screen dashboard.
 *
 * Phase 16 changes:
 *  - "Delivering to" header now shows real reverse-geocoded location.
 *  - Tapping the address chip triggers [LocationPermissionHelper] to obtain GPS,
 *    which then navigates to [MapPickerFragment] for manual correction if needed.
 *  - Location is requested ONLY when the user taps the header — never automatically.
 */
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory() }
    private val locationViewModel: CustomerLocationViewModel by viewModels {
        CustomerLocationViewModel.Factory(requireContext())
    }

    private lateinit var permissionHelper: LocationPermissionHelper

    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var trendingAdapter: FoodAdapter
    private lateinit var restaurantAdapter: RestaurantAdapter

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // Register BEFORE onCreateView — must be in onCreate per Activity Result API contract
        permissionHelper = LocationPermissionHelper.create(this) { state ->
            locationViewModel.onPermissionResult(state)
            when (state) {
                is LocationPermissionState.DeniedPermanently -> showPermanentDenialDialog()
                is LocationPermissionState.LocationDisabled  -> showLocationDisabledDialog()
                else -> Unit
            }
        }
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentHomeBinding.inflate(inflater, container, false)

    override fun setupUI() {
        setupAdapters()
        setupListeners()
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { renderState(it) } }
                launch { locationViewModel.uiState.collect { renderLocationState(it) } }
            }
        }
    }

    private fun setupAdapters() {
        bannerAdapter = BannerAdapter { offer ->
            context?.showToast("Clicked Banner: ${offer.title}")
        }
        binding.rvBanners.adapter = bannerAdapter

        categoryAdapter = CategoryAdapter { category ->
            context?.showToast("Filtering Category: ${category.name}")
            navigateTo(R.id.nav_search)
        }
        binding.rvCategories.adapter = categoryAdapter

        trendingAdapter = FoodAdapter { food ->
            context?.showToast("Food Selected: ${food.name}")
        }
        binding.rvTrending.adapter = trendingAdapter

        restaurantAdapter = RestaurantAdapter { restaurant ->
            context?.showToast("Restaurant Selected: ${restaurant.name}")
        }
        binding.rvRestaurants.adapter = restaurantAdapter
    }

    private fun setupListeners() {
        binding.cardSearchEntry.setOnClickListener {
            navigateTo(R.id.action_homeFragment_to_searchFragment)
        }

        binding.btnNotifications.setOnClickListener {
            context?.showToast("Notifications placeholder tapped")
        }

        // Phase 16: tap address header → request location permission then resolve position
        binding.tvAddress.setOnClickListener {
            onLocationHeaderTapped()
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadHomeData()
        }
    }

    /**
     * Called when user deliberately taps the "Delivering to" header.
     * Checks current permission state and either fetches GPS or shows a rationale.
     */
    private fun onLocationHeaderTapped() {
        when (val state = permissionHelper.currentState()) {
            is LocationPermissionState.Granted -> {
                locationViewModel.fetchCurrentLocation()
            }
            is LocationPermissionState.DeniedPermanently -> {
                showPermanentDenialDialog()
            }
            is LocationPermissionState.LocationDisabled -> {
                showLocationDisabledDialog()
            }
            else -> {
                // Not yet requested or denied once — show rationale then request
                showLocationRationaleDialog {
                    permissionHelper.requestLocationPermission()
                }
            }
        }
    }

    private fun renderLocationState(state: com.foodfusionai.app.ui.location.CustomerLocationUiState) {
        binding.tvAddress.text = state.deliveryLabel

        state.userMessage?.let { msg ->
            context?.showToast(msg)
            locationViewModel.clearMessage()
        }
        state.locationError?.let { err ->
            context?.showToast(err)
            locationViewModel.clearMessage()
        }
    }

    private fun renderState(state: HomeUiState) {
        if (state.isLoading) {
            binding.progressBar.show()
            binding.scrollViewHome.hide()
            binding.layoutError.hide()
            return
        }

        if (state.error != null) {
            binding.progressBar.hide()
            binding.scrollViewHome.hide()
            binding.layoutError.show()
            binding.tvErrorMessage.text = state.error
            return
        }

        binding.progressBar.hide()
        binding.layoutError.hide()
        binding.scrollViewHome.show()

        val name = state.currentUser?.displayName ?: ""
        binding.tvGreeting.text = if (name.isNotBlank()) "Good evening, $name" else "Welcome!"

        bannerAdapter.submitList(state.banners)
        categoryAdapter.submitList(state.categories)
        trendingAdapter.submitList(state.trendingFoods)
        restaurantAdapter.submitList(state.restaurants)
    }

    // ── Permission dialogs ────────────────────────────────────────────────────

    private fun showLocationRationaleDialog(onAccept: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enable Location")
            .setMessage("Location permission is required to find restaurants near you and calculate accurate delivery time.")
            .setPositiveButton("Allow") { _, _ -> onAccept() }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun showPermanentDenialDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Location Permission Required")
            .setMessage("Location access was permanently denied. Open Settings to enable it so we can find restaurants near you.")
            .setPositiveButton("Open Settings") { _, _ -> permissionHelper.openAppSettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLocationDisabledDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Location Services Off")
            .setMessage("Please enable location services so FoodFusion can find restaurants near you.")
            .setPositiveButton("Enable") { _, _ -> permissionHelper.openLocationSettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
