package com.foodfusionai.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.data.repository.SearchRepository
import com.foodfusionai.app.data.repository.SearchRepositoryImpl
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel governing Food Search and Restaurant Discovery.
 * Combines filters, query input, and sorting reactive states to compute results locally.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: SearchRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Full database cache loaded once for rapid client-side filtering/debounce
    private var allFoodsCache: List<Food> = emptyList()
    private var allRestaurantsCache: List<Restaurant> = emptyList()

    private val queryInputFlow = MutableStateFlow("")

    init {
        observeRecentSearches()
        loadDiscoveryBaseData()
        observeQueryInput()
    }

    private fun observeRecentSearches() {
        scope.launch {
            repository.getRecentSearches().collect { entities ->
                val searchList = entities.map { it.query }
                _uiState.update { it.copy(recentSearches = searchList) }
            }
        }
    }

    private fun observeQueryInput() {
        scope.launch {
            // Debounce input to filter efficiently without lag on rapid typing
            queryInputFlow.debounce(300).collect { debouncedQuery ->
                _uiState.update { it.copy(query = debouncedQuery) }
                executeSearchQuery(debouncedQuery)
            }
        }
    }

    fun loadDiscoveryBaseData() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val categoriesRes = repository.getCategories()
            val restaurantsRes = repository.getRestaurants()
            val foodsRes = repository.getFoods()

            val hasError = categoriesRes is Resource.Error ||
                    restaurantsRes is Resource.Error ||
                    foodsRes is Resource.Error

            if (hasError) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Unable to load directory information."
                    )
                }
                return@launch
            }

            val categories = (categoriesRes as? Resource.Success)?.data ?: emptyList()
            allRestaurantsCache = (restaurantsRes as? Resource.Success)?.data ?: emptyList()
            allFoodsCache = (foodsRes as? Resource.Success)?.data ?: emptyList()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    categories = categories
                )
            }

            // Apply filter updates based on current query input
            executeSearchQuery(queryInputFlow.value)
        }
    }

    /**
     * Updates input query buffer from text box.
     */
    fun updateQueryInput(input: String) {
        queryInputFlow.value = input

        // Generate matching autocomplete suggestions based on categories/foods/restaurants names
        if (input.length >= 2) {
            val lowerInput = input.lowercase().trim()
            val foodSugs = allFoodsCache.map { it.name }
            val restSugs = allRestaurantsCache.map { it.name }
            val catSugs = _uiState.value.categories.map { it.name }
            
            val mergedSuggestions = (foodSugs + restSugs + catSugs)
                .filter { it.lowercase().contains(lowerInput) }
                .distinct()
                .take(5)
            
            _uiState.update { it.copy(suggestions = mergedSuggestions) }
        } else {
            _uiState.update { it.copy(suggestions = emptyList()) }
        }
    }

    /**
     * Submits a finalized query for recent search tracking and result retrieval.
     */
    fun submitSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            scope.launch {
                repository.insertRecentSearch(trimmed)
            }
        }
        updateQueryInput(trimmed)
    }

    /**
     * Removes an individual search query from history.
     */
    fun deleteRecentSearch(query: String) {
        scope.launch {
            repository.deleteRecentSearch(query)
        }
    }

    /**
     * Clears all search history.
     */
    fun clearRecentSearches() {
        scope.launch {
            repository.clearRecentSearches()
        }
    }

    /**
     * Applies filter parameters.
     */
    fun applyFilters(filters: SearchFilters) {
        _uiState.update { it.copy(selectedFilters = filters) }
        executeSearchQuery(queryInputFlow.value)
    }

    /**
     * Clears/resets filter selections.
     */
    fun resetFilters() {
        _uiState.update { it.copy(selectedFilters = SearchFilters()) }
        executeSearchQuery(queryInputFlow.value)
    }

    /**
     * Applies sorting selection.
     */
    fun applySorting(sort: SearchSort) {
        _uiState.update { it.copy(selectedSort = sort) }
        executeSearchQuery(queryInputFlow.value)
    }

    private fun executeSearchQuery(query: String) {
        val lowerQuery = query.lowercase().trim()
        val filters = _uiState.value.selectedFilters
        val sort = _uiState.value.selectedSort

        // 1. Filter Foods
        var filteredFoods = allFoodsCache.filter { food ->
            // Search text match
            val matchesSearch = lowerQuery.isEmpty() ||
                    food.name.lowercase().contains(lowerQuery) ||
                    food.description.lowercase().contains(lowerQuery)

            // Category filter
            val matchesCategory = filters.category == null || 
                    food.categoryId == filters.category ||
                    getCategoryName(food.categoryId).lowercase() == filters.category.lowercase()

            // Price filter
            val matchesPrice = filters.maxPrice == null || food.price <= filters.maxPrice

            // Rating filter
            val matchesRating = filters.minRating == null || food.rating >= filters.minRating

            // Vegetarian filter
            val matchesVeg = filters.isVegetarian == null || food.isVegetarian == filters.isVegetarian

            matchesSearch && matchesCategory && matchesPrice && matchesRating && matchesVeg
        }

        // 2. Filter Restaurants
        var filteredRestaurants = allRestaurantsCache.filter { rest ->
            // Search text match
            val matchesSearch = lowerQuery.isEmpty() ||
                    rest.name.lowercase().contains(lowerQuery) ||
                    rest.description.lowercase().contains(lowerQuery)

            // Category filter (restaurants list matches categories tags)
            val matchesCategory = filters.category == null ||
                    rest.categories.contains(filters.category) ||
                    rest.categories.any { getCategoryName(it).lowercase() == filters.category.lowercase() }

            // Rating filter
            val matchesRating = filters.minRating == null || rest.rating >= filters.minRating

            // Delivery time filter
            val deliveryMinutes = parseDeliveryTime(rest.deliveryTime)
            val matchesDelivery = filters.maxDeliveryTimeMinutes == null || deliveryMinutes <= filters.maxDeliveryTimeMinutes

            // Open Now filter
            val matchesOpenNow = filters.isOpenNow == null || filters.isOpenNow == false || rest.isOpen == true

            matchesSearch && matchesCategory && matchesRating && matchesDelivery && matchesOpenNow
        }

        // 3. Sort Foods
        filteredFoods = when (sort) {
            SearchSort.RATING_DESC -> filteredFoods.sortedByDescending { it.rating }
            SearchSort.PRICE_ASC -> filteredFoods.sortedBy { it.price }
            SearchSort.PRICE_DESC -> filteredFoods.sortedByDescending { it.price }
            else -> filteredFoods // Relevance/Default
        }

        // 4. Sort Restaurants
        filteredRestaurants = when (sort) {
            SearchSort.RATING_DESC -> filteredRestaurants.sortedByDescending { it.rating }
            SearchSort.DELIVERY_TIME_ASC -> filteredRestaurants.sortedBy { parseDeliveryTime(it.deliveryTime) }
            else -> filteredRestaurants
        }

        val totalResults = filteredFoods.size + filteredRestaurants.size

        _uiState.update {
            it.copy(
                foods = filteredFoods,
                restaurants = filteredRestaurants,
                resultCount = totalResults
            )
        }
    }

    private fun getCategoryName(categoryId: String): String {
        return _uiState.value.categories.find { it.id == categoryId }?.name ?: ""
    }

    private fun parseDeliveryTime(timeStr: String): Int {
        // e.g. "25 mins" -> 25
        val digitStr = timeStr.filter { it.isDigit() }
        return digitStr.toIntOrNull() ?: 999
    }

    /**
     * Factory class.
     */
    class Factory(
        private val repository: SearchRepository = SearchRepositoryImpl()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                return SearchViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
