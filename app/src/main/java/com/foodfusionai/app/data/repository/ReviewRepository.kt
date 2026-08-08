package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Review
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface ReviewRepository {
    fun getReviewsForTarget(targetId: String, limit: Long = 20): Flow<Resource<List<Review>>>
    
    suspend fun createReview(
        orderId: String,
        restaurantId: String,
        foodId: String?,
        rating: Int,
        comment: String,
        userName: String
    ): Resource<String>
    
    suspend fun editReview(
        reviewId: String,
        rating: Int,
        comment: String
    ): Resource<Unit>
    
    suspend fun deleteReview(reviewId: String): Resource<Unit>
    
    suspend fun interactReview(reviewId: String, action: String, reason: String? = null): Resource<Unit>
}
