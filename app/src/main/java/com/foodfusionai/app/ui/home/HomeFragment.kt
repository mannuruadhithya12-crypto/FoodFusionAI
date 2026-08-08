package com.foodfusionai.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentHomeBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.ui.home.adapters.BannerAdapter
import com.foodfusionai.app.ui.home.adapters.CategoryAdapter
import com.foodfusionai.app.ui.home.adapters.FoodAdapter
import com.foodfusionai.app.ui.home.adapters.RestaurantAdapter
import com.foodfusionai.app.utils.hide
import com.foodfusionai.app.utils.show
import com.foodfusionai.app.utils.showToast
import kotlinx.coroutines.launch

/**
 * Fragment rendering the Home screen dashboard.
 * Coordinates user interaction, triggers page navigation,
 * and binds ViewModel state feeds to UI components.
 */
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory() }

    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var trendingAdapter: FoodAdapter
    private lateinit var restaurantAdapter: RestaurantAdapter

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentHomeBinding.inflate(inflater, container, false)

    override fun setupUI() {
        setupAdapters()
        setupListeners()
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
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
            // Phase 2 Filter Navigation Foundation: Can navigate to Search with category argument later
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
        // Search entry point click -> navigate to SearchFragment
        binding.cardSearchEntry.setOnClickListener {
            navigateTo(R.id.action_homeFragment_to_searchFragment)
        }

        // Notification entry point click
        binding.btnNotifications.setOnClickListener {
            context?.showToast("Notifications placeholder tapped")
        }

        // Address entry point click
        binding.tvAddress.setOnClickListener {
            context?.showToast("Address management placeholder tapped")
        }

        // Retry on error state
        binding.btnRetry.setOnClickListener {
            viewModel.loadHomeData()
        }
    }

    private fun renderState(state: HomeUiState) {
        // Handle Loading state
        if (state.isLoading) {
            binding.progressBar.show()
            binding.scrollViewHome.hide()
            binding.layoutError.hide()
            return
        }

        // Handle Error state
        if (state.error != null) {
            binding.progressBar.hide()
            binding.scrollViewHome.hide()
            binding.layoutError.show()
            binding.tvErrorMessage.text = state.error
            return
        }

        // Handle Success State
        binding.progressBar.hide()
        binding.layoutError.hide()
        binding.scrollViewHome.show()

        // Set User greeting
        val name = state.currentUser?.displayName ?: ""
        if (name.isNotBlank()) {
            binding.tvGreeting.text = "Good evening, $name"
        } else {
            binding.tvGreeting.text = "Welcome!"
        }

        // Set Address
        binding.tvAddress.text = "Saved Address"

        // Submit lists to Adapters
        bannerAdapter.submitList(state.banners)
        categoryAdapter.submitList(state.categories)
        trendingAdapter.submitList(state.trendingFoods)
        restaurantAdapter.submitList(state.restaurants)
    }
}
