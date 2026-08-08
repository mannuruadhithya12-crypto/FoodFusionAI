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
        Restaurant(id = "r1", name = "Pizza Palace", description = "Cheesy Italian Pizzas", imageUrl = "https://mock.foodfusion.ai/r1.png", rating = 4.5, deliveryTime = "25 mins", deliveryFee = 29.0, address = "MG Road", isOpen = true, categories = listOf("c1")),
        Restaurant(id = "r2", name = "Burger Bistro", description = "Juicy Gourmet Burgers", imageUrl = "https://mock.foodfusion.ai/r2.png", rating = 4.2, deliveryTime = "15 mins", deliveryFee = 39.0, address = "Sector 15", isOpen = true, categories = listOf("c2")),
        Restaurant(id = "r3", name = "Biryani House", description = "Authentic Mughlai Biryanis", imageUrl = "https://mock.foodfusion.ai/r3.png", rating = 4.7, deliveryTime = "35 mins", deliveryFee = 49.0, address = "Connaught Place", isOpen = true, categories = listOf("c3"))
    )

    private fun getMockFoods(): List<Food> = listOf(
        Food(id = "f1", restaurantId = "r1", categoryId = "c1", name = "Margherita Pizza", description = "Fresh mozzarella and basil", price = 249.0, imageUrl = "https://mock.foodfusion.ai/f1.png", rating = 4.6, isAvailable = true, isVegetarian = true, ingredients = listOf("Cheese", "Tomato Sauce")),
        Food(id = "f2", restaurantId = "r2", categoryId = "c2", name = "Cheese Burst Burger", description = "Loaded double patty burger", price = 189.0, imageUrl = "https://mock.foodfusion.ai/f2.png", rating = 4.3, isAvailable = true, isVegetarian = false, ingredients = listOf("Beef", "Cheese")),
        Food(id = "f3", restaurantId = "r3", categoryId = "c3", name = "Chicken Dum Biryani", description = "Fragrant basmati rice with spices", price = 299.0, imageUrl = "https://mock.foodfusion.ai/f3.png", rating = 4.8, isAvailable = true, isVegetarian = false, ingredients = listOf("Chicken", "Rice"))
    )
}
