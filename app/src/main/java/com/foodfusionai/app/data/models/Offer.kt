package com.foodfusionai.app.data.models

data class Offer(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val validUntil: Long = 0L,
    val restaurantId: String? = null
)
