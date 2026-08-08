package com.foodfusionai.app.ui.restaurant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.data.repository.RestaurantRepository
import com.foodfusionai.app.data.repository.RestaurantRepositoryImpl
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing the Restaurant Details view state.
 * Debounces menu search queries locally and filters items.
 */
@OptIn(FlowPreview::class)
class RestaurantDetailsViewModel(
    private val repository: RestaurantRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(RestaurantDetailsUiState())
    val uiState: StateFlow<RestaurantDetailsUiState> = _uiState.asStateFlow()

    private val menuSearchInputFlow = MutableStateFlow("")

    init {
        scope.launch {
            menuSearchInputFlow.debounce(250).collect { query ->
                _uiState.update { it.copy(menuQuery = query) }
                filterMenu()
            }
        }
    }

    /**
     * Triggers initial fetch of restaurant details, category chips, and menu list.
     */
    fun loadRestaurantData(restaurantId: String) {
        if (restaurantId.isBlank() || restaurantId == "unknown") {
            _uiState.update { it.copy(isLoading = false, error = "Invalid restaurant information.") }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val restRes = repository.getRestaurantById(restaurantId)
            val menuRes = repository.getMenuByRestaurant(restaurantId)
            val catsRes = repository.getCategories()

            val hasError = restRes is Resource.Error || menuRes is Resource.Error || catsRes is Resource.Error
            if (hasError) {
                val errorMsg = when {
                    restRes is Resource.Error -> restRes.message
                    menuRes is Resource.Error -> menuRes.message
                    else -> "Failed to load restaurant details."
                }
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                return@launch
            }

            val restaurant = (restRes as? Resource.Success)?.data
            val menu = (menuRes as? Resource.Success)?.data ?: emptyList()
            val allCats = (catsRes as? Resource.Success)?.data ?: emptyList()

            if (restaurant == null) {
                _uiState.update { it.copy(isLoading = false, error = "Restaurant not found.") }
                return@launch
            }

            // Identify which categories actually exist in this restaurant's menu list
            val menuCatIds = menu.map { it.categoryId }.distinct()
            val categories = allCats.filter { it.id in menuCatIds }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    restaurant = restaurant,
                    categories = categories,
                    menu = menu,
                    isRestaurantOpen = restaurant.isOpen
                )
            }

            // Populate initial filtered menu list
            filterMenu()
        }
    }

    /**
     * Updates sub-menu search keyword buffer.
     */
    fun updateMenuSearchInput(input: String) {
        menuSearchInputFlow.value = input
        if (coroutineScope != null) {
            _uiState.update { it.copy(menuQuery = input) }
            filterMenu()
        }
    }

    /**
     * Applies a category filter to list results.
     */
    fun selectCategoryFilter(categoryId: String?) {
        _uiState.update { it.copy(selectedCategory = categoryId) }
        filterMenu()
    }

    private fun filterMenu() {
        val selectedCat = _uiState.value.selectedCategory
        val query = _uiState.value.menuQuery.lowercase().trim()
        val allMenu = _uiState.value.menu

        val filtered = allMenu.filter { food ->
            val matchesCategory = selectedCat == null || food.categoryId == selectedCat
            val matchesSearch = query.isEmpty() || 
                    food.name.lowercase().contains(query) || 
                    food.description.lowercase().contains(query)

            matchesCategory && matchesSearch
        }

        _uiState.update { it.copy(filteredMenu = filtered) }
    }

    /**
     * Factory class.
     */
    class Factory(
        private val repository: RestaurantRepository = RestaurantRepositoryImpl()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RestaurantDetailsViewModel::class.java)) {
                return RestaurantDetailsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
