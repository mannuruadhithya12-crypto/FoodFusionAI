package com.foodfusionai.app.ui.favorites

import com.foodfusionai.app.data.models.Favorite

data class FavoriteUiState(
    val favorites: List<Favorite> = emptyList(),
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val error: String? = null
) {
    val favoriteRestaurants: List<Favorite>
        get() = favorites.filter { it.targetType.uppercase() == "RESTAURANT" }

    val favoriteFoods: List<Favorite>
        get() = favorites.filter { it.targetType.uppercase() == "FOOD" }
}
