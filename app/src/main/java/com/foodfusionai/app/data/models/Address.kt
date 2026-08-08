package com.foodfusionai.app.data.models

data class Address(
    val id: String = "",
    val userId: String = "",
    val type: String = "Home", // Home, Work, Other
    val recipientName: String = "",
    val phoneNumber: String = "",
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val landmark: String = "",
    val instructions: String = "",
    val isDefault: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
