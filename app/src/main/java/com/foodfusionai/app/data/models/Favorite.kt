package com.foodfusionai.app.data.models

data class Favorite(
    val id: String = "",
    val userId: String = "",
    val foodId: String = "",
    val restaurantId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
