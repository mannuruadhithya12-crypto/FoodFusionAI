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
        val fs = firestore ?: return@withContext Resource.Success(null)
        try {
            val document = fs.collection("restaurants").document(id).get().await()
            val restaurant = document.toObject(Restaurant::class.java)
            Resource.Success(restaurant)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load restaurant details from Firestore.", e)
            Resource.Success(null)
        }
    }

    override suspend fun getMenuByRestaurant(restaurantId: String): Resource<List<Food>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(emptyList())
        try {
            val snapshot = fs.collection("foods")
                .whereEqualTo("restaurantId", restaurantId)
                .get()
                .await()
            val list = snapshot.toObjects(Food::class.java)
            Resource.Success(list)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load restaurant menu from Firestore.", e)
            Resource.Success(emptyList())
        }
    }

    override suspend fun getCategories(): Resource<List<Category>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(emptyList())
        try {
            val snapshot = fs.collection("categories").get().await()
            val list = snapshot.toObjects(Category::class.java)
            Resource.Success(list)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load categories.", e)
            Resource.Success(emptyList())
        }
    }

    override suspend fun getFoodById(id: String): Resource<Food?> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(null)
        try {
            val document = fs.collection("foods").document(id).get().await()
            val food = document.toObject(Food::class.java)
            Resource.Success(food)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load food details from Firestore.", e)
            Resource.Success(null)
        }
    }
}
