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
        val db = firestore ?: return@withContext Resource.Success(emptyList())
        try {
            val snapshot = db.collection("offers").get().await()
            val list = snapshot.toObjects(Offer::class.java)
            Resource.Success(list)
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore offers fetch failed", e)
            Resource.Success(emptyList())
        }
    }

    override suspend fun getCategories(): Resource<List<Category>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Resource.Success(emptyList())
        try {
            val snapshot = db.collection("categories").get().await()
            val list = snapshot.toObjects(Category::class.java)
            Resource.Success(list)
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore categories fetch failed", e)
            Resource.Success(emptyList())
        }
    }

    override suspend fun getRestaurants(): Resource<List<Restaurant>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Resource.Success(emptyList())
        try {
            val snapshot = db.collection("restaurants").whereEqualTo("isOpen", true).get().await()
            val list = snapshot.toObjects(Restaurant::class.java)
            Resource.Success(list)
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore restaurants fetch failed", e)
            Resource.Success(emptyList())
        }
    }

    override suspend fun getTrendingFoods(): Resource<List<Food>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Resource.Success(emptyList())
        try {
            val snapshot = db.collection("foods")
                .whereEqualTo("popular", true)
                .limit(10)
                .get()
                .await()
            val list = snapshot.toObjects(Food::class.java)
            Resource.Success(list)
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore trending foods fetch failed", e)
            Resource.Success(emptyList())
        }
    }
}
