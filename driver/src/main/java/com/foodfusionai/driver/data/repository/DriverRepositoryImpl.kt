package com.foodfusionai.driver.data.repository

import android.util.Log
import com.foodfusionai.driver.data.models.Driver
import com.foodfusionai.driver.data.models.Order
import com.foodfusionai.driver.data.models.OrderStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class DriverRepositoryImpl(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : DriverRepository {

    override fun login(email: String, password: String): Flow<Result<Driver>> = flow {
        try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("Auth failed: empty user")
            
            // Get profile from Firestore
            val doc = firestore.collection("drivers").document(uid).get().await()
            if (!doc.exists()) {
                throw Exception("Driver profile not found. Please register as a driver.")
            }
            val driver = doc.toObject(Driver::class.java)!!
            
            // Update FCM token
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                firestore.collection("drivers").document(uid).update("fcmToken", token).await()
            } catch (err: Exception) {
                Log.e("DriverRepositoryImpl", "FCM token update failed", err)
            }

            emit(Result.success(driver))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun register(email: String, password: String, profile: Driver): Flow<Result<Driver>> = flow {
        try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("Auth failed: empty user")
            
            val driverToSave = profile.copy(
                uid = uid,
                email = email,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Save to Firestore with PENDING state
            firestore.collection("drivers").document(uid).set(driverToSave).await()
            
            // Try updating FCM token
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                firestore.collection("drivers").document(uid).update("fcmToken", token).await()
            } catch (err: Exception) {
                Log.e("DriverRepositoryImpl", "FCM token registration failed", err)
            }

            emit(Result.success(driverToSave))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun getDriverProfile(driverId: String): Flow<Result<Driver>> = flow {
        try {
            val doc = firestore.collection("drivers").document(driverId).get().await()
            if (doc.exists()) {
                emit(Result.success(doc.toObject(Driver::class.java)!!))
            } else {
                emit(Result.failure(Exception("Profile not found")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun observeDriverProfile(driverId: String): Flow<Result<Driver>> = callbackFlow {
        val listener = firestore.collection("drivers").document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val driver = snapshot.toObject(Driver::class.java)
                    if (driver != null) {
                        trySend(Result.success(driver))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    override fun updateDriverAvailability(driverId: String, availability: String): Flow<Result<Boolean>> = flow {
        try {
            firestore.collection("drivers").document(driverId)
                .update(
                    "availability", availability,
                    "updatedAt", System.currentTimeMillis()
                ).await()
            emit(Result.success(true))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun observeActiveOffers(driverId: String): Flow<Result<List<Map<String, Any>>>> = callbackFlow {
        val query = firestore.collection("driverOffers")
            .whereEqualTo("driverId", driverId)
            .whereEqualTo("status", "PENDING")

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val now = System.currentTimeMillis()
                val offers: List<Map<String, Any>> = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data
                    if (data != null) {
                        val expiresAt = data["expiresAt"] as? Long ?: 0L
                        if (expiresAt > now) {
                            data
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
                trySend(Result.success(offers))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun observeActiveOrder(driverId: String): Flow<Result<Order?>> = callbackFlow {
        val query = firestore.collection("orders")
            .whereEqualTo("deliveryPartner.id", driverId)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val activeOrder = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Order::class.java)
                }.firstOrNull { order ->
                    order.orderStatus != OrderStatus.DELIVERED && 
                    order.orderStatus != OrderStatus.CANCELLED
                }
                trySend(Result.success(activeOrder))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun acceptOffer(offerId: String): Flow<Result<String>> = flow {
        try {
            val result = functions.getHttpsCallable("acceptDeliveryAssignment")
                .call(mapOf("offerId" to offerId))
                .await()
            val data = result.data as? Map<*, *>
            val orderId = data?.get("orderId") as? String ?: ""
            emit(Result.success(orderId))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun declineOffer(offerId: String): Flow<Result<Boolean>> = flow {
        try {
            functions.getHttpsCallable("declineDeliveryAssignment")
                .call(mapOf("offerId" to offerId))
                .await()
            emit(Result.success(true))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun collectOrder(orderId: String): Flow<Result<Boolean>> = flow {
        try {
            functions.getHttpsCallable("updateDeliveryStatus")
                .call(mapOf("orderId" to orderId, "newStatus" to "OUT_FOR_DELIVERY"))
                .await()
            emit(Result.success(true))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun completeDelivery(orderId: String, otp: String): Flow<Result<Boolean>> = flow {
        try {
            functions.getHttpsCallable("verifyDeliveryOtp")
                .call(mapOf("orderId" to orderId, "otp" to otp))
                .await()
            emit(Result.success(true))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun reportIssue(orderId: String, reason: String, description: String): Flow<Result<Boolean>> = flow {
        try {
            functions.getHttpsCallable("reportDeliveryIssue")
                .call(mapOf(
                    "orderId" to orderId,
                    "reason" to reason,
                    "description" to description
                ))
                .await()
            emit(Result.success(true))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun getEarningsSummary(driverId: String): Flow<Result<Map<String, Any>>> = flow {
        try {
            val doc = firestore.collection("driverEarnings").document(driverId).get().await()
            val data = doc.data ?: emptyMap<String, Any>()
            emit(Result.success(data))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun getEarningsEntries(driverId: String): Flow<Result<List<Map<String, Any>>>> = flow {
        try {
            val snap = firestore.collection("driverEarnings").document(driverId)
                .collection("entries")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            val entries = snap.documents.mapNotNull { it.data }
            emit(Result.success(entries))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun getDeliveryHistory(driverId: String): Flow<Result<List<Order>>> = flow {
        try {
            val snap = firestore.collection("orders")
                .whereEqualTo("deliveryPartner.id", driverId)
                .get()
                .await()
            val orders = snap.documents.mapNotNull { doc ->
                val order = doc.toObject(Order::class.java)
                if (order != null && (order.orderStatus == OrderStatus.DELIVERED || order.orderStatus == OrderStatus.CANCELLED)) {
                    order
                } else {
                    null
                }
            }.sortedByDescending { it.createdAt }
            emit(Result.success(orders))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun logout() {
        val uid = auth.uid
        if (uid != null) {
            // Reset availability to OFFLINE before logging out
            firestore.collection("drivers").document(uid).update("availability", "OFFLINE")
            // Remove FCM token to prevent push notifications after logout
            firestore.collection("drivers").document(uid).update("fcmToken", null)
        }
        auth.signOut()
    }
}
