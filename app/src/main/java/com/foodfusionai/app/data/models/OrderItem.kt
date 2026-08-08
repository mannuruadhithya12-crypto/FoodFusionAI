package com.foodfusionai.app.data.models

data class OrderItem(
    val foodId: String = "",
    val foodName: String = "",
    val quantity: Int = 1,
    val price: Double = 0.0,
    val imageUrl: String = "",
    val customizations: String = "" // JSON or comma separated string
)
