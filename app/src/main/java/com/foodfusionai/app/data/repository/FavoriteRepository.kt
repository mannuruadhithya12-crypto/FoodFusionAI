package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Favorite
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun observeFavorites(): Flow<Resource<List<Favorite>>>
    suspend fun isFavorite(targetId: String): Resource<Boolean>
    suspend fun toggleFavorite(
        targetId: String,
        targetType: String,
        targetName: String,
        imageUrl: String,
        restaurantId: String
    ): Resource<Boolean>
    suspend fun clearFavorites(): Resource<Unit> // Usually called on logout or account deletion
}
