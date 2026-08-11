package com.foodfusionai.app.data.models

import com.foodfusionai.app.data.models.order.OrderItem
data class Cart(
    val id: String = "",
    val userId: String = "",
    val items: List<OrderItem> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
