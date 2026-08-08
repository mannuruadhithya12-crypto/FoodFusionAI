package com.foodfusionai.app.data.models

data class Address(
    val id: String = "",
    val type: String = "Home", // Home, Work, Other
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val instructions: String = ""
)
