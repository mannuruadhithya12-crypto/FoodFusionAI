package com.foodfusionai.app.data.models

data class Restaurant(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val rating: Double = 0.0,
    val deliveryTime: String = "",
    val deliveryFee: Double = 0.0,
    val address: String = "",
    val isOpen: Boolean = false,
    val categories: List<String> = emptyList()
)
