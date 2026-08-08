package com.foodfusionai.app.data.models

data class Favorite(
    val id: String = "",
    val userId: String = "",
    val targetId: String = "",
    val targetType: String = "", // "RESTAURANT" or "FOOD"
    val targetName: String = "",
    val imageUrl: String = "",
    val restaurantId: String = "", // Populated for food targets to link back
    val createdAt: Long = System.currentTimeMillis()
)
