package com.foodfusionai.app.ui.search

import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant

/**
 * Filter configurations for search criteria.
 */
data class SearchFilters(
    val category: String? = null,
    val maxPrice: Double? = null,
    val minRating: Double? = null,
    val isVegetarian: Boolean? = null,
    val maxDeliveryTimeMinutes: Int? = null
)

/**
 * Sort strategies for results listing.
 */
enum class SearchSort {
    RELEVANCE,
    RATING_DESC,
    PRICE_ASC,
    PRICE_DESC,
    DELIVERY_TIME_ASC
}

/**
 * Coherent UI state mapping for Search screen.
 */
data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val popularSearches: List<String> = listOf("Biryani", "Pizza", "Burger", "Desserts", "Chinese"),
    val categories: List<Category> = emptyList(),
    val restaurants: List<Restaurant> = emptyList(),
    val foods: List<Food> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedFilters: SearchFilters = SearchFilters(),
    val selectedSort: SearchSort = SearchSort.RELEVANCE,
    val resultCount: Int = 0
)
