package com.foodfusionai.app.data.models

data class Cart(
    val id: String = "",
    val userId: String = "",
    val items: List<OrderItem> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
