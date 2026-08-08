package com.foodfusionai.app.ui.food

import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant

data class FoodDetailsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val food: Food? = null,
    val restaurant: Restaurant? = null,
    val quantity: Int = 1,
    val subtotal: Double = 0.0,
    val selectedSize: String = "Medium",
    val selectedSpiceLevel: String = "Medium",
    val isAvailable: Boolean = false
)
