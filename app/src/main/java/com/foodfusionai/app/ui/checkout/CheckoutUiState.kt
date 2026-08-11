package com.foodfusionai.app.ui.checkout

import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.models.Address
import com.foodfusionai.app.data.models.Coupon
import com.foodfusionai.app.data.models.DeliveryValidationResult

data class CheckoutUiState(
    val cartItems: List<CartEntity> = emptyList(),
    val selectedAddress: Address? = null,
    val couponCode: String = "",
    val appliedCoupon: Coupon? = null,
    val deliveryInstructions: String = "",
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val discount: Double = 0.0,
    val payableTotal: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val validationMessage: String? = null,
    val checkoutValidationPassed: Boolean = false,

    // Phase 16: server-authoritative delivery validation
    val deliveryValidation: DeliveryValidationResult? = null,
    val isValidatingDelivery: Boolean = false
)
