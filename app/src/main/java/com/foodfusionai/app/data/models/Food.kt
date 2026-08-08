package com.foodfusionai.app.data.models

data class Food(
    val id: String = "",
    val restaurantId: String = "",
    val categoryId: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val ratingSum: Double = 0.0,
    val ratingDistribution: Map<String, Int> = emptyMap(),
    val isAvailable: Boolean = true,
    val isVegetarian: Boolean = false,
    val ingredients: List<String> = emptyList(),
    val popular: Boolean = false
)
