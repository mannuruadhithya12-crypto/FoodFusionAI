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
        val fs = firestore ?: return@withContext Resource.Success(emptyList())
        try {
            val snapshot = fs.collection("categories").get().await()
            val list = snapshot.toObjects(Category::class.java)
            Resource.Success(list)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load categories from Firestore.", e)
            Resource.Success(emptyList())
        }
    }

    override suspend fun getRestaurants(): Resource<List<Restaurant>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(emptyList())
        try {
            val snapshot = fs.collection("restaurants").whereEqualTo("isOpen", true).get().await()
            val list = snapshot.toObjects(Restaurant::class.java)
            Resource.Success(list)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load restaurants from Firestore.", e)
            Resource.Success(emptyList())
        }
    }

    override suspend fun getFoods(): Resource<List<Food>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Resource.Success(emptyList())
        try {
            val snapshot = fs.collection("foods").whereEqualTo("isAvailable", true).get().await()
            val list = snapshot.toObjects(Food::class.java)
            Resource.Success(list)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load foods from Firestore.", e)
            Resource.Success(emptyList())
        }
    }
}
