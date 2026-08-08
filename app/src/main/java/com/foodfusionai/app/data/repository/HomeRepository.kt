package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Offer
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.utils.Resource

/**
 * Interface contract for Home screen data operations.
 */
interface HomeRepository {

    /**
     * Fetches all active promotional offers/banners.
     */
    suspend fun getOffers(): Resource<List<Offer>>

    /**
     * Fetches all food categories.
     */
    suspend fun getCategories(): Resource<List<Category>>

    /**
     * Fetches all popular restaurants.
     */
    suspend fun getRestaurants(): Resource<List<Restaurant>>

    /**
     * Fetches top trending foods.
     */
    suspend fun getTrendingFoods(): Resource<List<Food>>
}
