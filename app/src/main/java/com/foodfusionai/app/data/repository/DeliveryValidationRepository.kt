package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.DeliveryValidationResult
import com.foodfusionai.app.utils.Resource
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Calls the `validateDeliveryLocation` and `calculateDeliveryFee` Cloud Functions
 * so that delivery eligibility and fee are always determined server-side.
 *
 * The Android client NEVER trusts its own distance calculation for pricing.
 * The local [DistanceCalculator] is only used for display / sorting purposes.
 *
 * Part M, N, O: delivery radius, zone check, and server-authoritative fee.
 */
class DeliveryValidationRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {

    /**
     * Asks the backend whether the customer's address is within the
     * restaurant's delivery range, and what the fee should be.
     *
     * @param restaurantId    Firestore restaurant document ID
     * @param customerLat     Customer delivery address latitude
     * @param customerLon     Customer delivery address longitude
     */
    suspend fun validateDelivery(
        restaurantId: String,
        customerLat: Double,
        customerLon: Double
    ): Resource<DeliveryValidationResult> {
        return try {
            val data = hashMapOf(
                "restaurantId"   to restaurantId,
                "customerLat"    to customerLat,
                "customerLon"    to customerLon
            )

            val result = functions
                .getHttpsCallable("validateDeliveryLocation")
                .call(data)
                .await()

            val map = result.data as? Map<*, *>
                ?: return Resource.Error("Invalid response from server")

            val validationResult = DeliveryValidationResult(
                isDeliverable = map["isDeliverable"] as? Boolean ?: false,
                distanceKm    = (map["distanceKm"] as? Number)?.toDouble() ?: 0.0,
                deliveryFee   = (map["deliveryFee"] as? Number)?.toDouble() ?: 0.0,
                reason        = map["reason"] as? String ?: "",
                zoneId        = map["zoneId"] as? String ?: ""
            )

            Resource.Success(validationResult)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Delivery validation failed")
        }
    }
}
