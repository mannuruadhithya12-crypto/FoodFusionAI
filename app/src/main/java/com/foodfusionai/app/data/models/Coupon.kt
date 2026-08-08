package com.foodfusionai.app.data.models

data class Coupon(
    val id: String = "",
    val code: String = "",
    val description: String = "",
    val discountPercentage: Double = 0.0,
    val maxDiscountAmount: Double = 0.0,
    val minOrderAmount: Double = 0.0,
    val validUntil: Long = 0L,
    val isActive: Boolean = true
)
