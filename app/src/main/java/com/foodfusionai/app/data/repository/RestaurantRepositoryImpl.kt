package com.foodfusionai.app.data.repository

import android.content.Context
import android.util.Log
import com.foodfusionai.app.FoodFusionApp
import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.utils.Resource
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [RestaurantRepository] querying Firestore.
 */
class RestaurantRepositoryImpl(
    private val context: Context? = try { FoodFusionApp.instance } catch (_: Throwable) { null }
) : RestaurantRepository {

    companion object {
        private const val TAG = "RestaurantRepositoryImpl"
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            val ctx = context ?: return@lazy null
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(ctx)
            }
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase Firestore failed to initialize. Fallback Mode active.", e)
            null
        }
    }

    override suspend fun getRestaurantById(id: String): Resource<Restaurant?> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(getMockRestaurants().find { it.id == id })
        try {
            val document = fs.collection("restaurants").document(id).get().await()
            val restaurant = document.toObject(Restaurant::class.java)
            if (restaurant == null) {
                // controlled fallback check
                Resource.Success(getMockRestaurants().find { it.id == id })
            } else {
                Resource.Success(restaurant)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load restaurant details from Firestore. Using fallback.", e)
            Resource.Success(getMockRestaurants().find { it.id == id })
        }
    }

    override suspend fun getMenuByRestaurant(restaurantId: String): Resource<List<Food>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(getMockFoods().filter { it.restaurantId == restaurantId })
        try {
            val snapshot = fs.collection("foods")
                .whereEqualTo("restaurantId", restaurantId)
                .get()
                .await()
            val list = snapshot.toObjects(Food::class.java)
            if (list.isEmpty()) {
                Resource.Success(getMockFoods().filter { it.restaurantId == restaurantId })
            } else {
                Resource.Success(list)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load restaurant menu from Firestore. Using fallback.", e)
            Resource.Success(getMockFoods().filter { it.restaurantId == restaurantId })
        }
    }

    override suspend fun getCategories(): Resource<List<Category>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(getMockCategories())
        try {
            val snapshot = fs.collection("categories").get().await()
            val list = snapshot.toObjects(Category::class.java)
            if (list.isEmpty()) {
                Resource.Success(getMockCategories())
            } else {
                Resource.Success(list)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load categories. Using fallback.", e)
            Resource.Success(getMockCategories())
        }
    }

    override suspend fun getFoodById(id: String): Resource<Food?> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(getMockFoods().find { it.id == id })
        try {
            val document = fs.collection("foods").document(id).get().await()
            val food = document.toObject(Food::class.java)
            if (food == null) {
                Resource.Success(getMockFoods().find { it.id == id })
            } else {
                Resource.Success(food)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load food details from Firestore. Using fallback.", e)
            Resource.Success(getMockFoods().find { it.id == id })
        }
    }

    // -- Mock Fallbacks --

    private fun getMockCategories(): List<Category> = listOf(
        Category("c1", "Pizza", "https://mock.foodfusion.ai/pizza.png"),
        Category("c2", "Burger", "https://mock.foodfusion.ai/burger.png"),
        Category("c3", "Biryani", "https://mock.foodfusion.ai/biryani.png"),
        Category("c4", "Chinese", "https://mock.foodfusion.ai/chinese.png"),
        Category("c5", "Desserts", "https://mock.foodfusion.ai/desserts.png")
    )

    private fun getMockRestaurants(): List<Restaurant> = listOf(
        Restaurant("r1", "Pizza Palace", "Cheesy Italian Pizzas", "https://mock.foodfusion.ai/r1.png", 4.5, "25 mins", 29.0, "MG Road", true, listOf("c1")),
        Restaurant("r2", "Burger Bistro", "Juicy Gourmet Burgers", "https://mock.foodfusion.ai/r2.png", 4.2, "15 mins", 39.0, "Sector 15", true, listOf("c2")),
        Restaurant("r3", "Biryani House", "Authentic Mughlai Biryanis", "https://mock.foodfusion.ai/r3.png", 4.7, "35 mins", 49.0, "Connaught Place", true, listOf("c3"))
    )

    private fun getMockFoods(): List<Food> = listOf(
        Food("f1", "r1", "c1", "Margherita Pizza", "Fresh mozzarella and basil", 249.0, "https://mock.foodfusion.ai/f1.png", 4.6, true, true, listOf("Cheese", "Tomato Sauce")),
        Food("f2", "r2", "c2", "Cheese Burst Burger", "Loaded double patty burger", 189.0, "https://mock.foodfusion.ai/f2.png", 4.3, true, false, listOf("Beef", "Cheese")),
        Food("f3", "r3", "c3", "Chicken Dum Biryani", "Fragrant basmati rice with spices", 299.0, "https://mock.foodfusion.ai/f3.png", 4.8, true, false, listOf("Chicken", "Rice"))
    )
}
