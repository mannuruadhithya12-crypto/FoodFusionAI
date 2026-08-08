package com.foodfusionai.app.data.models

data class Review(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userImageUrl: String = "",
    val restaurantId: String = "",
    val foodId: String? = null,
    val rating: Double = 0.0,
    val comment: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
