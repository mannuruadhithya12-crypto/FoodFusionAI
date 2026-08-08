package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Coupon
import com.foodfusionai.app.utils.Resource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [CouponRepository] using Firebase Functions and Firestore.
 */
class CouponRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : CouponRepository {

    override suspend fun validateCoupon(code: String, cartTotal: Double): Resource<Coupon?> = withContext(Dispatchers.IO) {
        try {
            val data = hashMapOf(
                "couponCode" to code,
                "cartTotal" to cartTotal
            )
            val result = functions.getHttpsCallable("validateCoupon").call(data).await()
            val resultData = result.data as? Map<String, Any>
            
            if (resultData != null && resultData["isValid"] == true) {
                // Construct a temporary Coupon object with the validated data
                val coupon = Coupon(
                    id = resultData["couponId"] as? String ?: "",
                    code = resultData["code"] as? String ?: "",
                    description = resultData["description"] as? String ?: "",
                    discountPercentage = 0.0, // Backend already calculated discount
                    maxDiscountAmount = resultData["discount"] as? Double ?: (resultData["discount"] as? Number)?.toDouble() ?: 0.0,
                    isActive = true
                )
                Resource.Success(coupon)
            } else {
                Resource.Error("Invalid coupon.")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to validate coupon.")
        }
    }

    override suspend fun getAvailableCoupons(): Resource<List<Coupon>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("coupons")
                .whereEqualTo("isActive", true)
                .get()
                .await()
            val coupons = snapshot.documents.mapNotNull { it.toObject(Coupon::class.java) }
            Resource.Success(coupons)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to fetch coupons.")
        }
    }
}
