package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Favorite
import com.foodfusionai.app.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FavoriteRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : FavoriteRepository {

    override fun observeFavorites(): Flow<Resource<List<Favorite>>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(Resource.Error("User not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = firestore.collection("users").document(uid).collection("favorites")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load favorites"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val favorites = snapshot.toObjects(Favorite::class.java)
                    trySend(Resource.Success(favorites))
                } else {
                    trySend(Resource.Success(emptyList()))
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun isFavorite(targetId: String): Resource<Boolean> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("User not logged in")
        return try {
            val doc = firestore.collection("users").document(uid)
                .collection("favorites").document(targetId)
                .get()
                .await()
            Resource.Success(doc.exists())
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to check favorite status", e)
        }
    }

    override suspend fun toggleFavorite(
        targetId: String,
        targetType: String,
        targetName: String,
        imageUrl: String,
        restaurantId: String
    ): Resource<Boolean> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("User not logged in")
        return try {
            val ref = firestore.collection("users").document(uid).collection("favorites").document(targetId)
            val doc = ref.get().await()

            if (doc.exists()) {
                ref.delete().await()
                Resource.Success(false) // Not a favorite anymore
            } else {
                val favorite = Favorite(
                    id = targetId,
                    userId = uid,
                    targetId = targetId,
                    targetType = targetType,
                    targetName = targetName,
                    imageUrl = imageUrl,
                    restaurantId = restaurantId,
                    createdAt = System.currentTimeMillis()
                )
                ref.set(favorite).await()
                Resource.Success(true) // Is now a favorite
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to toggle favorite", e)
        }
    }

    override suspend fun clearFavorites(): Resource<Unit> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("User not logged in")
        return try {
            val snapshot = firestore.collection("users").document(uid).collection("favorites").get().await()
            firestore.runBatch { batch ->
                snapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to clear favorites", e)
        }
    }
}
