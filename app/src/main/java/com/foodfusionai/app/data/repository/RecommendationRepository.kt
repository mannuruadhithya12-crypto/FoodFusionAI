package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.RecommendationItem
import com.foodfusionai.app.utils.Resource

/**
 * Interface contract for fetching AI personalized food recommendations.
 */
interface RecommendationRepository {
    /**
     * Fetches personalized recommendations. 
     * Uses local cache first if available and not stale, otherwise calls backend Callable function.
     */
    suspend fun getPersonalizedRecommendations(forceRefresh: Boolean = false): Resource<List<RecommendationItem>>
}
