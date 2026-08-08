package com.foodfusionai.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foodfusionai.app.R
import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.databinding.DialogSearchFilterBinding
import com.foodfusionai.app.databinding.FragmentSearchBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.ui.home.adapters.FoodAdapter
import com.foodfusionai.app.ui.home.adapters.RestaurantAdapter
import com.foodfusionai.app.ui.search.adapters.RecentSearchAdapter
import com.foodfusionai.app.ui.search.adapters.SuggestionAdapter
import com.foodfusionai.app.utils.hide
import com.foodfusionai.app.utils.hideKeyboard
import com.foodfusionai.app.utils.show
import com.foodfusionai.app.utils.showToast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * Fragment governing query input, search filtering, and restaurant discovery.
 */
class SearchFragment : BaseFragment<FragmentSearchBinding>() {

    private val viewModel: SearchViewModel by viewModels { SearchViewModel.Factory() }

    private lateinit var recentSearchAdapter: RecentSearchAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var restaurantAdapter: RestaurantAdapter
    private lateinit var foodAdapter: FoodAdapter

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentSearchBinding.inflate(inflater, container, false)

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
        recentSearchAdapter = RecentSearchAdapter(
            onQueryClick = { term ->
                binding.etSearchQuery.setText(term)
                binding.etSearchQuery.setSelection(term.length)
                viewModel.submitSearchQuery(term)
                hideKeyboard()
            },
            onDeleteClick = { term ->
                viewModel.deleteRecentSearch(term)
            }
        )
        binding.rvRecentSearches.adapter = recentSearchAdapter

        suggestionAdapter = SuggestionAdapter { term ->
            binding.etSearchQuery.setText(term)
            binding.etSearchQuery.setSelection(term.length)
            viewModel.submitSearchQuery(term)
            hideKeyboard()
        }
        binding.rvSuggestions.adapter = suggestionAdapter

        restaurantAdapter = RestaurantAdapter { restaurant ->
            val bundle = Bundle().apply {
                putString("restaurantId", restaurant.id)
            }
            navigateTo(R.id.action_searchFragment_to_restaurantDetailsFragment, bundle)
        }
        binding.rvRestaurantResults.adapter = restaurantAdapter

        foodAdapter = FoodAdapter { food ->
            val bundle = Bundle().apply {
                putString("foodId", food.id)
            }
            navigateTo(R.id.action_searchFragment_to_foodDetailsFragment, bundle)
        }
        binding.rvFoodResults.adapter = foodAdapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            navigateBack()
        }

        binding.btnClearQuery.setOnClickListener {
            binding.etSearchQuery.text.clear()
            viewModel.updateQueryInput("")
        }

        binding.etSearchQuery.doAfterTextChanged { text ->
            val query = text.toString()
            binding.btnClearQuery.visibility = if (query.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            viewModel.updateQueryInput(query)
        }

        // Listen for keyboard search click action
        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchQuery.text.toString()
                viewModel.submitSearchQuery(query)
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.tvClearAllHistory.setOnClickListener {
            viewModel.clearRecentSearches()
        }

        binding.btnFilter.setOnClickListener {
            showFilterBottomSheet()
        }

        binding.chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedSort = when (checkedIds.firstOrNull()) {
                R.id.chipSortRating -> SearchSort.RATING_DESC
                R.id.chipSortPriceLow -> SearchSort.PRICE_ASC
                R.id.chipSortPriceHigh -> SearchSort.PRICE_DESC
                R.id.chipSortDelivery -> SearchSort.DELIVERY_TIME_ASC
                else -> SearchSort.RELEVANCE
            }
            viewModel.applySorting(selectedSort)
        }

        binding.btnClearFilters.setOnClickListener {
            viewModel.resetFilters()
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadDiscoveryBaseData()
        }
    }

    private fun renderState(state: SearchUiState) {
        // Handle loading
        if (state.isLoading) {
            binding.progressBar.show()
            binding.layoutInitialState.hide()
            binding.rvSuggestions.hide()
            binding.scrollViewResults.hide()
            binding.layoutControls.hide()
            binding.layoutEmpty.hide()
            binding.layoutError.hide()
            return
        }

        // Handle error
        if (state.error != null) {
            binding.progressBar.hide()
            binding.layoutInitialState.hide()
            binding.rvSuggestions.hide()
            binding.scrollViewResults.hide()
            binding.layoutControls.hide()
            binding.layoutEmpty.hide()
            binding.layoutError.show()
            binding.tvErrorMessage.text = state.error
            return
        }

        binding.progressBar.hide()
        binding.layoutError.hide()

        // Populate popular query chips once (only if not loaded yet)
        if (binding.chipGroupPopular.childCount == 0 && state.popularSearches.isNotEmpty()) {
            state.popularSearches.forEach { term ->
                val chip = Chip(context).apply {
                    text = term
                    isClickable = true
                    setOnClickListener {
                        binding.etSearchQuery.setText(term)
                        binding.etSearchQuery.setSelection(term.length)
                        viewModel.submitSearchQuery(term)
                        hideKeyboard()
                    }
                }
                binding.chipGroupPopular.addView(chip)
            }
        }

        // Show layout depending on query state
        val rawInput = binding.etSearchQuery.text.toString().trim()

        if (rawInput.isEmpty()) {
            // Initial State: show history/popular
            binding.layoutInitialState.show()
            binding.layoutControls.hide()
            binding.rvSuggestions.hide()
            binding.scrollViewResults.hide()
            binding.layoutEmpty.hide()

            recentSearchAdapter.submitList(state.recentSearches)
            binding.layoutRecentHeader.visibility = if (state.recentSearches.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        } else if (state.suggestions.isNotEmpty() && state.restaurants.isEmpty() && state.foods.isEmpty()) {
            // Suggestions State: show matching terms
            binding.layoutInitialState.hide()
            binding.layoutControls.hide()
            binding.rvSuggestions.show()
            binding.scrollViewResults.hide()
            binding.layoutEmpty.hide()

            suggestionAdapter.submitList(state.suggestions)
        } else {
            // Results State
            binding.layoutInitialState.hide()
            binding.rvSuggestions.hide()

            if (state.restaurants.isEmpty() && state.foods.isEmpty()) {
                binding.layoutControls.show()
                binding.scrollViewResults.hide()
                binding.layoutEmpty.show()
                binding.tvEmptySubtitle.text = "No items matched your query w/ selected filters."
            } else {
                binding.layoutEmpty.hide()
                binding.layoutControls.show()
                binding.scrollViewResults.show()

                binding.tvResultCount.text = "${state.resultCount} results found"

                // Restaurants section
                if (state.restaurants.isEmpty()) {
                    binding.tvRestaurantsHeading.hide()
                    binding.rvRestaurantResults.hide()
                } else {
                    binding.tvRestaurantsHeading.show()
                    binding.rvRestaurantResults.show()
                    restaurantAdapter.submitList(state.restaurants)
                }

                // Foods section
                if (state.foods.isEmpty()) {
                    binding.tvFoodsHeading.hide()
                    binding.rvFoodResults.hide()
                } else {
                    binding.tvFoodsHeading.show()
                    binding.rvFoodResults.show()
                    foodAdapter.submitList(state.foods)
                }
            }
        }
    }

    private fun showFilterBottomSheet() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val dialogBinding = DialogSearchFilterBinding.inflate(layoutInflater)
        bottomSheet.setContentView(dialogBinding.root)

        val state = viewModel.uiState.value
        val filters = state.selectedFilters

        // 1. Populate category chips dynamically
        state.categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category.name
                tag = category.id
                isCheckable = true
                isChecked = filters.category == category.id || filters.category == category.name
            }
            dialogBinding.chipGroupCategoryFilter.addView(chip)
        }

        // 2. Pre-select food preference
        if (filters.isVegetarian == true) {
            dialogBinding.chipFilterVeg.isChecked = true
        } else if (filters.isVegetarian == false) {
            dialogBinding.chipFilterNonVeg.isChecked = true
        }

        // 3. Pre-select budget
        when (filters.maxPrice) {
            100.0 -> dialogBinding.chipPrice100.isChecked = true
            200.0 -> dialogBinding.chipPrice200.isChecked = true
            300.0 -> dialogBinding.chipPrice300.isChecked = true
        }

        // 4. Pre-select rating
        when (filters.minRating) {
            3.5 -> dialogBinding.chipRating35.isChecked = true
            4.0 -> dialogBinding.chipRating40.isChecked = true
            4.5 -> dialogBinding.chipRating45.isChecked = true
        }

        // 5. Pre-select delivery limit
        when (filters.maxDeliveryTimeMinutes) {
            20 -> dialogBinding.chipDelivery20.isChecked = true
            30 -> dialogBinding.chipDelivery30.isChecked = true
            45 -> dialogBinding.chipDelivery45.isChecked = true
        }

        // 6. Pre-select Open Now
        if (filters.isOpenNow == true) {
            dialogBinding.chipOpenNow.isChecked = true
        }

        dialogBinding.btnReset.setOnClickListener {
            viewModel.resetFilters()
            bottomSheet.dismiss()
        }

        dialogBinding.btnApply.setOnClickListener {
            // Read Category selection
            val selectedCatChipId = dialogBinding.chipGroupCategoryFilter.checkedChipId
            val category = if (selectedCatChipId != android.view.View.NO_ID) {
                val chip = dialogBinding.chipGroupCategoryFilter.findViewById<Chip>(selectedCatChipId)
                chip.tag as? String ?: chip.text.toString()
            } else {
                null
            }

            // Read Veg preference
            val isVeg = when {
                dialogBinding.chipFilterVeg.isChecked -> true
                dialogBinding.chipFilterNonVeg.isChecked -> false
                else -> null
            }

            // Read Price limit
            val maxPrice = when {
                dialogBinding.chipPrice100.isChecked -> 100.0
                dialogBinding.chipPrice200.isChecked -> 200.0
                dialogBinding.chipPrice300.isChecked -> 300.0
                else -> null
            }

            // Read Rating limit
            val minRating = when {
                dialogBinding.chipRating35.isChecked -> 3.5
                dialogBinding.chipRating40.isChecked -> 4.0
                dialogBinding.chipRating45.isChecked -> 4.5
                else -> null
            }

            // Read Delivery time limit
            val maxDelivery = when {
                dialogBinding.chipDelivery20.isChecked -> 20
                dialogBinding.chipDelivery30.isChecked -> 30
                dialogBinding.chipDelivery45.isChecked -> 45
                else -> null
            }

            // Read Open Now
            val isOpenNow = if (dialogBinding.chipOpenNow.isChecked) true else null

            val newFilters = SearchFilters(
                category = category,
                maxPrice = maxPrice,
                minRating = minRating,
                isVegetarian = isVeg,
                maxDeliveryTimeMinutes = maxDelivery,
                isOpenNow = isOpenNow
            )
            viewModel.applyFilters(newFilters)
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }
}
