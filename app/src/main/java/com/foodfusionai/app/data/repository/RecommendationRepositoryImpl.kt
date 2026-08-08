package com.foodfusionai.app.data.repository

import android.util.Log
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.RecommendationItem
import com.foodfusionai.app.data.models.RecommendationReason
import com.foodfusionai.app.utils.Resource
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RecommendationRepositoryImpl(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val homeRepository: HomeRepository = HomeRepositoryImpl()
) : RecommendationRepository {

    companion object {
        private const val TAG = "RecommendationRepositoryImpl"
        private var cachedRecommendations: List<RecommendationItem>? = null
        private var lastFetchTime: Long = 0
        private const val CACHE_EXPIRATION_MS = 15 * 60 * 1000L // 15 minutes
    }

    override suspend fun getPersonalizedRecommendations(forceRefresh: Boolean): Resource<List<RecommendationItem>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedRecommendations != null && (now - lastFetchTime) < CACHE_EXPIRATION_MS) {
            return@withContext Resource.Success(cachedRecommendations!!)
        }

        try {
            val result = functions.getHttpsCallable("getRecommendations").call().await()
            val data = result.data as? Map<String, Any>
            val reasonText = data?.get("reason") as? String ?: "Trending today"
            
            val reason = when {
                reasonText.contains("past orders", ignoreCase = true) -> RecommendationReason.BASED_ON_PAST_ORDERS
                reasonText.contains("popular in", ignoreCase = true) -> RecommendationReason.POPULAR_IN_AREA
                else -> RecommendationReason.TRENDING
            }

            val recommendationsRaw = data?.get("recommendations") as? List<Map<String, Any>> ?: emptyList()
            
            val items = recommendationsRaw.mapNotNull { map ->
                try {
                    val food = Food(
                        id = map["id"] as? String ?: return@mapNotNull null,
                        restaurantId = map["restaurantId"] as? String ?: "",
                        categoryId = map["categoryId"] as? String ?: "",
                        name = map["name"] as? String ?: "",
                        description = map["description"] as? String ?: "",
                        price = (map["price"] as? Number)?.toDouble() ?: 0.0,
                        imageUrl = map["imageUrl"] as? String ?: "",
                        rating = (map["rating"] as? Number)?.toDouble() ?: 0.0,
                        isAvailable = map["isAvailable"] as? Boolean ?: true,
                        isVegetarian = map["isVegetarian"] as? Boolean ?: false,
                        ingredients = map["ingredients"] as? List<String> ?: emptyList()
                    )
                    RecommendationItem(food, reason)
                } catch (e: Exception) {
                    null
                }
            }

            if (items.isNotEmpty()) {
                cachedRecommendations = items
                lastFetchTime = now
                Resource.Success(items)
            } else {
                getLocalFallback()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching recommendations from backend, falling back to local.", e)
            getLocalFallback()
        }
    }

    private suspend fun getLocalFallback(): Resource<List<RecommendationItem>> {
        val trendingFoodsRes = homeRepository.getTrendingFoods()
        if (trendingFoodsRes is Resource.Success && trendingFoodsRes.data != null) {
            val items = trendingFoodsRes.data.map { RecommendationItem(it, RecommendationReason.TRENDING) }
            cachedRecommendations = items
            lastFetchTime = System.currentTimeMillis()
            return Resource.Success(items)
        }
        return Resource.Error("Could not load recommendations.")
    }
}
