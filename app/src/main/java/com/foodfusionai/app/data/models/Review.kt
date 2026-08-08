package com.foodfusionai.app.data.models

data class Review(
    val reviewId: String = "",
    val userId: String = "",
    val userName: String = "",
    val orderId: String = "",
    val restaurantId: String = "",
    val foodId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isEdited: Boolean = false,
    val helpfulCount: Int = 0,
    val reportCount: Int = 0
)
