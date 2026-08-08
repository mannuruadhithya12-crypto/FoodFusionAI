package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Coupon
import com.foodfusionai.app.utils.Resource

/**
 * Interface contract for Coupon and Promotions operations.
 */
interface CouponRepository {

    /**
     * Checks if a coupon code is valid via backend validation.
     */
    suspend fun validateCoupon(code: String, cartTotal: Double): Resource<Coupon?>

    /**
     * Obtains list of all available coupons.
     */
    suspend fun getAvailableCoupons(): Resource<List<Coupon>>
}
