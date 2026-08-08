package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.local.room.entity.RecentSearchEntity
import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Interface contract for Search and Discovery data operations.
 */
interface SearchRepository {

    /**
     * Obtains an observable flow of Room-backed recent search queries.
     */
    fun getRecentSearches(): Flow<List<RecentSearchEntity>>

    /**
     * Inserts a search query.
     */
    suspend fun insertRecentSearch(query: String)

    /**
     * Deletes a specific query.
     */
    suspend fun deleteRecentSearch(query: String)

    /**
     * Clears all queries.
     */
    suspend fun clearRecentSearches()

    /**
     * Fetches all categories.
     */
    suspend fun getCategories(): Resource<List<Category>>

    /**
     * Fetches all restaurants.
     */
    suspend fun getRestaurants(): Resource<List<Restaurant>>

    /**
     * Fetches all foods.
     */
    suspend fun getFoods(): Resource<List<Food>>
}
