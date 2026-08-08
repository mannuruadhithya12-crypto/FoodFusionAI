package com.foodfusionai.app.ui.cart

import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.models.Coupon

data class CartUiState(
    val items: List<CartEntity> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val discount: Double = 0.0,
    val couponDiscount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val itemCount: Int = 0,
    val appliedCoupon: Coupon? = null,
    val validationMessage: String? = null,
    val isLoading: Boolean = false,
    val isEmpty: Boolean = false,
    val error: String? = null,
    val canCheckout: Boolean = false
)
