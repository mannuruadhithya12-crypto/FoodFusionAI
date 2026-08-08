package com.foodfusionai.app.data.repository

import android.content.Context
import android.util.Log
import com.foodfusionai.app.FoodFusionApp
import com.foodfusionai.app.data.local.room.FoodFusionDatabase
import com.foodfusionai.app.data.local.room.entity.RecentSearchEntity
import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.utils.Resource
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [SearchRepository] utilizing Room and Firebase.
 */
class SearchRepositoryImpl(
    private val context: Context? = try { FoodFusionApp.instance } catch (_: Throwable) { null }
) : SearchRepository {

    companion object {
        private const val TAG = "SearchRepositoryImpl"
    }

    private val db: FoodFusionDatabase? by lazy {
        context?.let { FoodFusionDatabase.getDatabase(it) }
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

    override fun getRecentSearches(): Flow<List<RecentSearchEntity>> {
        val dao = db?.recentSearchDao()
        return dao?.getRecentSearches() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    override suspend fun insertRecentSearch(query: String) = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext
        val dao = db?.recentSearchDao() ?: return@withContext
        dao.insertSearch(RecentSearchEntity(query.trim(), System.currentTimeMillis()))
    }

    override suspend fun deleteRecentSearch(query: String) = withContext(Dispatchers.IO) {
        val dao = db?.recentSearchDao() ?: return@withContext
        dao.deleteSearch(RecentSearchEntity(query, 0L)) // timestamp not used for delete comparison by PrimaryKey
    }

    override suspend fun clearRecentSearches() = withContext(Dispatchers.IO) {
        val dao = db?.recentSearchDao() ?: return@withContext
        dao.clearRecentSearches()
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
            Log.w(TAG, "Failed to load categories from Firestore. Using fallbacks.", e)
            Resource.Success(getMockCategories())
        }
    }

    override suspend fun getRestaurants(): Resource<List<Restaurant>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(getMockRestaurants())
        try {
            val snapshot = fs.collection("restaurants").get().await()
            val list = snapshot.toObjects(Restaurant::class.java)
            if (list.isEmpty()) {
                Resource.Success(getMockRestaurants())
            } else {
                Resource.Success(list)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load restaurants from Firestore. Using fallbacks.", e)
            Resource.Success(getMockRestaurants())
        }
    }

    override suspend fun getFoods(): Resource<List<Food>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(getMockFoods())
        try {
            val snapshot = fs.collection("foods").get().await()
            val list = snapshot.toObjects(Food::class.java)
            if (list.isEmpty()) {
                Resource.Success(getMockFoods())
            } else {
                Resource.Success(list)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load foods from Firestore. Using fallbacks.", e)
            Resource.Success(getMockFoods())
        }
    }

    // -- Controlled Mock Fallbacks --

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
