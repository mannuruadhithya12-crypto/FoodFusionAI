package com.foodfusionai.app.data.models

data class Order(
    val id: String = "",
    val userId: String = "",
    val restaurantId: String = "",
    val items: List<OrderItem> = emptyList(),
    val status: String = "", // e.g. PENDING, PREPARING, DELIVERING, COMPLETED, CANCELLED
    val totalAmount: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val discount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val deliveryAddress: Address? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val estimatedDeliveryTime: Long = 0L,
    val driverId: String? = null
)
