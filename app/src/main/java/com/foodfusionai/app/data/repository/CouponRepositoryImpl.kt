package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Coupon
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [CouponRepository] checking local promotions.
 */
class CouponRepositoryImpl : CouponRepository {

    private val availableCoupons = listOf(
        Coupon(
            id = "coupon_1",
            code = "FOOD20",
            description = "20% OFF on orders above ₹500 (Max discount ₹200)",
            discountPercentage = 20.0,
            maxDiscountAmount = 200.0,
            minOrderAmount = 500.0,
            validUntil = System.currentTimeMillis() + 86400000L, // valid for 1 day
            isActive = true
        ),
        Coupon(
            id = "coupon_2",
            code = "WELCOME100",
            description = "Flat 10% OFF on all orders above ₹300 (Max discount ₹100)",
            discountPercentage = 10.0,
            maxDiscountAmount = 100.0,
            minOrderAmount = 300.0,
            validUntil = System.currentTimeMillis() + 86400000L,
            isActive = true
        )
    )

    override suspend fun validateCoupon(code: String): Resource<Coupon?> = withContext(Dispatchers.IO) {
        val coupon = availableCoupons.find { it.code.equals(code.trim(), ignoreCase = true) }
        if (coupon != null && coupon.isActive) {
            Resource.Success(coupon)
        } else {
            Resource.Error("Invalid or expired coupon code.")
        }
    }

    override suspend fun getAvailableCoupons(): Resource<List<Coupon>> = withContext(Dispatchers.IO) {
        Resource.Success(availableCoupons)
    }
}
