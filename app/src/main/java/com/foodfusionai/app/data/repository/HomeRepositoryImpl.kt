package com.foodfusionai.app.data.repository

import android.content.Context
import android.util.Log
import com.foodfusionai.app.FoodFusionApp
import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Offer
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.utils.Resource
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Production implementation of [HomeRepository].
 * Pulls categories, restaurants, offers, and trending foods from Firestore.
 * Supports defensive offline fallback mode.
 */
class HomeRepositoryImpl(
    private val context: Context? = try { FoodFusionApp.instance } catch (_: Throwable) { null }
) : HomeRepository {

    companion object {
        private const val TAG = "HomeRepositoryImpl"
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

    override suspend fun getOffers(): Resource<List<Offer>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Resource.Success(getMockOffers())
        try {
            val snapshot = db.collection("offers").get().await()
            val list = snapshot.toObjects(Offer::class.java)
            if (list.isEmpty()) {
                Resource.Success(getMockOffers())
            } else {
                Resource.Success(list)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore offers fetch failed, using fallback mock offers", e)
            Resource.Success(getMockOffers())
        }
    }

    override suspend fun getCategories(): Resource<List<Category>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Resource.Success(getMockCategories())
        try {
            val snapshot = db.collection("categories").get().await()
            val list = snapshot.toObjects(Category::class.java)
            if (list.isEmpty()) {
                Resource.Success(getMockCategories())
            } else {
                Resource.Success(list)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore categories fetch failed, using fallback mock categories", e)
            Resource.Success(getMockCategories())
        }
    }

    override suspend fun getRestaurants(): Resource<List<Restaurant>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Resource.Success(getMockRestaurants())
        try {
            val snapshot = db.collection("restaurants").get().await()
            val list = snapshot.toObjects(Restaurant::class.java)
            if (list.isEmpty()) {
                Resource.Success(getMockRestaurants())
            } else {
                Resource.Success(list)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore restaurants fetch failed, using fallback mock restaurants", e)
            Resource.Success(getMockRestaurants())
        }
    }

    override suspend fun getTrendingFoods(): Resource<List<Food>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Resource.Success(getMockTrendingFoods())
        try {
            // Fetch popular items
            val snapshot = db.collection("foods")
                .whereEqualTo("popular", true)
                .limit(10)
                .get()
                .await()
            val list = snapshot.toObjects(Food::class.java)
            if (list.isEmpty()) {
                Resource.Success(getMockTrendingFoods())
            } else {
                Resource.Success(list)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore trending foods fetch failed, using fallback mock foods", e)
            Resource.Success(getMockTrendingFoods())
        }
    }

    // -- Offline Mocks --

    private fun getMockOffers(): List<Offer> = listOf(
        Offer("o1", "50% OFF on first order", "Use code WELCOME50", "https://mock.foodfusion.ai/banner1.png", 0L, null),
        Offer("o2", "Free Delivery this weekend", "Min order value ₹200", "https://mock.foodfusion.ai/banner2.png", 0L, null),
        Offer("o3", "Biryani Feast Specials", "Flat ₹100 off on select outlets", "https://mock.foodfusion.ai/banner3.png", 0L, null)
    )

    private fun getMockCategories(): List<Category> = listOf(
        Category("c1", "Pizza", "https://mock.foodfusion.ai/pizza.png"),
        Category("c2", "Burger", "https://mock.foodfusion.ai/burger.png"),
        Category("c3", "Biryani", "https://mock.foodfusion.ai/biryani.png"),
        Category("c4", "Chinese", "https://mock.foodfusion.ai/chinese.png"),
        Category("c5", "Desserts", "https://mock.foodfusion.ai/desserts.png")
    )

    private fun getMockRestaurants(): List<Restaurant> = listOf(
        Restaurant("r1", "Pizza Palace", "Cheesy Italian Pizzas", "https://mock.foodfusion.ai/r1.png", 4.5, "25-30 mins", 29.0, "MG Road", true, listOf("c1")),
        Restaurant("r2", "Burger Bistro", "Juicy Gourmet Burgers", "https://mock.foodfusion.ai/r2.png", 4.2, "20-25 mins", 39.0, "Sector 15", true, listOf("c2")),
        Restaurant("r3", "Biryani House", "Authentic Mughlai Biryanis", "https://mock.foodfusion.ai/r3.png", 4.7, "35-40 mins", 49.0, "Connaught Place", true, listOf("c3"))
    )

    private fun getMockTrendingFoods(): List<Food> = listOf(
        Food("f1", "r1", "c1", "Margherita Pizza", "Fresh mozzarella and basil", 249.0, "https://mock.foodfusion.ai/f1.png", 4.6, true, true, listOf("Cheese", "Tomato Sauce")),
        Food("f2", "r2", "c2", "Cheese Burst Burger", "Loaded double patty burger", 189.0, "https://mock.foodfusion.ai/f2.png", 4.3, true, false, listOf("Beef", "Cheese")),
        Food("f3", "r3", "c3", "Chicken Dum Biryani", "Fragrant basmati rice with spices", 299.0, "https://mock.foodfusion.ai/f3.png", 4.8, true, false, listOf("Chicken", "Rice"))
    )
}
