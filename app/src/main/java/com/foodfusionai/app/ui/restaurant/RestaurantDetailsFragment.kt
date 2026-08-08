package com.foodfusionai.app.ui.restaurant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.foodfusionai.app.R
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.databinding.FragmentRestaurantDetailsBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.ui.favorites.FavoriteViewModel
import com.foodfusionai.app.ui.restaurant.adapters.MenuFoodAdapter
import com.foodfusionai.app.utils.hide
import com.foodfusionai.app.utils.loadImage
import com.foodfusionai.app.utils.show
import com.foodfusionai.app.utils.showToast
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class RestaurantDetailsFragment : BaseFragment<FragmentRestaurantDetailsBinding>() {

    private val viewModel: RestaurantDetailsViewModel by viewModels { RestaurantDetailsViewModel.Factory() }
    private val favoriteViewModel: FavoriteViewModel by viewModels { FavoriteViewModel.Factory() }
    private lateinit var menuFoodAdapter: MenuFoodAdapter

    private var restaurantId: String = "unknown"

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRestaurantDetailsBinding.inflate(inflater, container, false)

    override fun setupUI() {
        // Safe argument retrieval with fallback checks
        restaurantId = arguments?.let {
            try {
                RestaurantDetailsFragmentArgs.fromBundle(it).restaurantId
            } catch (_: Throwable) {
                it.getString("restaurantId")
            }
        } ?: "unknown"

        setupAdapter()
        setupListeners()

        viewModel.loadRestaurantData(restaurantId)
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoriteViewModel.uiState.collect { state ->
                    val isFav = state.favoriteRestaurants.any { it.targetId == restaurantId }
                    binding.btnFavorite.setColorFilter(if (isFav) requireContext().getColor(R.color.md_theme_light_error) else requireContext().getColor(android.R.color.white))
                }
            }
        }
    }

    private fun setupAdapter() {
        menuFoodAdapter = MenuFoodAdapter(
            onFoodClick = { food ->
                val bundle = Bundle().apply {
                    putString("foodId", food.id)
                }
                navigateTo(R.id.action_restaurantDetailsFragment_to_foodDetailsFragment, bundle)
            },
            onAddClick = { food ->
                // Quick add from menu page with default customizations
                val bundle = Bundle().apply {
                    putString("foodId", food.id)
                }
                navigateTo(R.id.action_restaurantDetailsFragment_to_foodDetailsFragment, bundle)
            }
        )
        binding.rvMenuFoods.adapter = menuFoodAdapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            navigateBack()
        }

        binding.etMenuSearch.doAfterTextChanged { text ->
            viewModel.updateMenuSearchInput(text.toString())
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadRestaurantData(restaurantId)
        }
        
        binding.btnFavorite.setOnClickListener {
            val restaurant = viewModel.uiState.value.restaurant
            if (restaurant != null) {
                favoriteViewModel.toggleFavorite(
                    targetId = restaurant.id,
                    targetType = "RESTAURANT",
                    targetName = restaurant.name,
                    imageUrl = restaurant.imageUrl,
                    restaurantId = ""
                )
            }
        }
        
        binding.tvRestaurantRating.setOnClickListener {
            val action = RestaurantDetailsFragmentDirections.actionRestaurantDetailsFragmentToReviewListFragment(targetId = restaurantId)
            findNavController().navigate(action)
        }
    }

    private fun renderState(state: RestaurantDetailsUiState) {
        if (state.isLoading) {
            binding.progressBar.show()
            binding.scrollViewRestaurant.hide()
            binding.layoutError.hide()
            return
        }

        if (state.error != null) {
            binding.progressBar.hide()
            binding.scrollViewRestaurant.hide()
            binding.layoutError.show()
            binding.tvErrorMessage.text = state.error
            return
        }

        binding.progressBar.hide()
        binding.layoutError.hide()
        binding.scrollViewRestaurant.show()

        val restaurant = state.restaurant ?: return
        
        // Render Header profiles
        binding.tvRestaurantTitle.text = restaurant.name
        binding.tvRestaurantCuisine.text = restaurant.description
        binding.tvRestaurantRating.text = String.format("%.1f ★ (%d reviews)", restaurant.rating, restaurant.ratingCount)
        binding.tvRestaurantDelivery.text = restaurant.deliveryTime
        binding.tvRestaurantDistance.text = restaurant.address

        if (restaurant.imageUrl.isNotEmpty()) {
            binding.ivRestaurantHero.loadImage(restaurant.imageUrl)
        }

        // Availability status configuration
        if (state.isRestaurantOpen) {
            binding.tvStatusBadge.text = "OPEN"
            binding.tvStatusBadge.setBackgroundColor(resources.getColor(R.color.color_success, null))
            binding.tvRestaurantOffer.show()
        } else {
            binding.tvStatusBadge.text = "CLOSED"
            binding.tvStatusBadge.setBackgroundColor(resources.getColor(R.color.md_theme_light_error, null))
            binding.tvRestaurantOffer.hide()
            requireContext().showToast("Ordering is currently unavailable because this outlet is closed.")
        }

        // Configure horizontal Category filter chips
        if (binding.chipGroupMenuCategories.childCount == 0 && state.categories.isNotEmpty()) {
            // Add 'All Dishes' default chip
            val allChip = Chip(requireContext()).apply {
                text = "All"
                isCheckable = true
                isChecked = state.selectedCategory == null
                setOnClickListener {
                    viewModel.selectCategoryFilter(null)
                }
            }
            binding.chipGroupMenuCategories.addView(allChip)

            state.categories.forEach { category ->
                val chip = Chip(requireContext()).apply {
                    text = category.name
                    tag = category.id
                    isCheckable = true
                    isChecked = state.selectedCategory == category.id
                    setOnClickListener {
                        viewModel.selectCategoryFilter(category.id)
                    }
                }
                binding.chipGroupMenuCategories.addView(chip)
            }
        }

        // Render adapter items
        menuFoodAdapter.submitList(state.filteredMenu)
        
        if (state.filteredMenu.isEmpty()) {
            binding.tvMenuTitle.text = "No items available"
        } else {
            binding.tvMenuTitle.text = if (state.selectedCategory == null) "All Dishes" else "Filtered Selection"
        }
    }
}
