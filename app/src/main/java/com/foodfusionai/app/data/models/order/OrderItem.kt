package com.foodfusionai.app.data.models.order

data class OrderItem(
    val foodId: String = "",
    val foodName: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val subtotal: Double = 0.0,
    val imageUrl: String = "",
    val size: String? = null,
    val spice: String? = null,
    val customizationsJson: String? = null
)
