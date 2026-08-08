package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Notification
import com.foodfusionai.app.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepositoryImpl(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : NotificationRepository {

    override fun observeNotifications(): Flow<Resource<List<Notification>>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(Resource.Error("User not authenticated"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val subscription = firestore.collection("users")
            .document(uid)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Unknown error occurred"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val notifications = snapshot.documents.mapNotNull { it.toObject(Notification::class.java) }
                    trySend(Resource.Success(notifications))
                }
            }

        awaitClose { subscription.remove() }
    }

    override suspend fun markAsRead(notificationId: String): Resource<Unit> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("User not authenticated")
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("notifications")
                .document(notificationId)
                .update("isRead", true)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to mark as read")
        }
    }

    override suspend fun markAllAsRead(): Resource<Unit> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("User not authenticated")
        return try {
            val unreadDocs = firestore.collection("users")
                .document(uid)
                .collection("notifications")
                .whereEqualTo("isRead", false)
                .get()
                .await()

            firestore.runBatch { batch ->
                unreadDocs.documents.forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                }
            }.await()

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to mark all as read")
        }
    }

    override suspend fun deleteNotification(notificationId: String): Resource<Unit> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("User not authenticated")
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("notifications")
                .document(notificationId)
                .delete()
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to delete notification")
        }
    }
}
