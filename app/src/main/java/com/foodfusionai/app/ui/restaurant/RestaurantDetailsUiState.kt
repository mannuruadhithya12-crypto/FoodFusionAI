package com.foodfusionai.app.ui.restaurant

import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant

data class RestaurantDetailsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val restaurant: Restaurant? = null,
    val categories: List<Category> = emptyList(),
    val menu: List<Food> = emptyList(),
    val filteredMenu: List<Food> = emptyList(),
    val selectedCategory: String? = null, // Category name or ID
    val menuQuery: String = "",
    val isRestaurantOpen: Boolean = false
)
