package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.utils.Resource

/**
 * Interface contract for Restaurant menu and catalog data operations.
 */
interface RestaurantRepository {

    /**
     * Obtains restaurant details by ID.
     */
    suspend fun getRestaurantById(id: String): Resource<Restaurant?>

    /**
     * Obtains menu items listing for a given restaurant.
     */
    suspend fun getMenuByRestaurant(restaurantId: String): Resource<List<Food>>

    /**
     * Obtains food categories listing.
     */
    suspend fun getCategories(): Resource<List<Category>>

    /**
     * Obtains details for an individual food item.
     */
    suspend fun getFoodById(id: String): Resource<Food?>
}
