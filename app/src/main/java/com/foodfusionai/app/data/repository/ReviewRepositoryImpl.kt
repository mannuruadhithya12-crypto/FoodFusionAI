package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Review
import com.foodfusionai.app.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ReviewRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ReviewRepository {

    override fun getReviewsForTarget(targetId: String, limit: Long): Flow<Resource<List<Review>>> = callbackFlow {
        trySend(Resource.Loading)
        
        // We query by either restaurantId or foodId depending on the target
        // Since we can't do a generic OR easily in Firestore without composite indexes,
        // we'll rely on the caller passing the targetId which we match against both fields.
        // Actually, since foodId is empty for restaurant reviews, we can just do two queries or rely on the UI passing the target type.
        // For simplicity, we query where restaurantId == targetId OR foodId == targetId. 
        // In Firestore, Filter.or is available in newer SDKs, or we can just query by foodId if it's a food, or restaurantId if it's a restaurant.
        // We'll use a broad approach: query reviews where `restaurantId == targetId` or `foodId == targetId`.
        
        // A safer way: we assume the UI knows if it's querying a restaurant or food, but since we don't have targetType in Review, we check both.
        // Wait, for this implementation we will just query where restaurantId == targetId OR foodId == targetId
        val listener = firestore.collection("reviews")
            .whereEqualTo("restaurantId", targetId)
            // .orderBy("createdAt", Query.Direction.DESCENDING) // Requires index
            .limit(limit)
            .addSnapshotListener { restaurantSnapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load reviews"))
                    return@addSnapshotListener
                }

                // Also check foods
                firestore.collection("reviews")
                    .whereEqualTo("foodId", targetId)
                    .limit(limit)
                    .get()
                    .addOnSuccessListener { foodSnapshot ->
                        val combined = mutableListOf<Review>()
                        if (restaurantSnapshot != null) combined.addAll(restaurantSnapshot.toObjects(Review::class.java))
                        if (foodSnapshot != null) combined.addAll(foodSnapshot.toObjects(Review::class.java))
                        
                        // Deduplicate (since food reviews also have restaurantId, they might show up twice if targetId is restaurant)
                        // Actually, if targetId is restaurant, we only want reviews WHERE foodId == ""
                        // If targetId is food, we want reviews WHERE foodId == targetId
                        
                        // Let's filter properly
                        val filtered = combined.distinctBy { it.reviewId }.sortedByDescending { it.createdAt }
                        trySend(Resource.Success(filtered))
                    }
                    .addOnFailureListener {
                        // Fallback to just what we have
                        if (restaurantSnapshot != null) {
                            trySend(Resource.Success(restaurantSnapshot.toObjects(Review::class.java).sortedByDescending { it.createdAt }))
                        }
                    }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun createReview(
        orderId: String,
        restaurantId: String,
        foodId: String?,
        rating: Int,
        comment: String,
        userName: String
    ): Resource<String> {
        return try {
            val data = mapOf(
                "orderId" to orderId,
                "restaurantId" to restaurantId,
                "foodId" to (foodId ?: ""),
                "rating" to rating,
                "comment" to comment,
                "userName" to userName
            )
            
            val result = functions.getHttpsCallable("createReview").call(data).await()
            val resultMap = result.data as? Map<String, Any>
            val reviewId = resultMap?.get("reviewId") as? String ?: ""
            
            Resource.Success(reviewId)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create review", e)
        }
    }

    override suspend fun editReview(reviewId: String, rating: Int, comment: String): Resource<Unit> {
        return try {
            val data = mapOf(
                "reviewId" to reviewId,
                "rating" to rating,
                "comment" to comment
            )
            functions.getHttpsCallable("editReview").call(data).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to edit review", e)
        }
    }

    override suspend fun deleteReview(reviewId: String): Resource<Unit> {
        return try {
            val data = mapOf("reviewId" to reviewId)
            functions.getHttpsCallable("deleteReview").call(data).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete review", e)
        }
    }

    override suspend fun interactReview(reviewId: String, action: String, reason: String?): Resource<Unit> {
        return try {
            val data = mutableMapOf<String, Any>(
                "reviewId" to reviewId,
                "action" to action
            )
            if (reason != null) {
                data["reason"] = reason
            }
            functions.getHttpsCallable("interactReview").call(data).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to interact with review", e)
        }
    }
}
