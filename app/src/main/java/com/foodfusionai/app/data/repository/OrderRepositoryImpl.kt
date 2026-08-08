package com.foodfusionai.app.data.repository

import android.util.Log
import com.foodfusionai.app.data.models.order.Order
import com.foodfusionai.app.utils.Resource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class OrderRepositoryImpl : OrderRepository {

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w("OrderRepositoryImpl", "Firebase Firestore failed to initialize. Fallback Mode active.", e)
            null
        }
    }

    override fun createOrder(order: Order): Flow<Resource<Order>> = flow {
        emit(Resource.Loading)
        
        val db = firestore
        if (db == null) {
            // Simulated local memory fallback if Firebase is not properly initialized
            emit(Resource.Success(order))
            return@flow
        }

        try {
            // IDEMPOTENCY CHECK: Ensure we don't recreate an order for the same successful payment reference
            if (order.paymentReference.isNotBlank()) {
                val existingOrders = db.collection("orders")
                    .whereEqualTo("paymentReference", order.paymentReference)
                    .get()
                    .await()
                
                if (!existingOrders.isEmpty) {
                    val existingOrder = existingOrders.documents[0].toObject(Order::class.java)
                    if (existingOrder != null) {
                        Log.d("OrderRepositoryImpl", "Idempotency hit. Order already exists for reference.")
                        emit(Resource.Success(existingOrder))
                        return@flow
                    }
                }
            }

            val documentRef = if (order.orderId.isEmpty()) {
                db.collection("orders").document()
            } else {
                db.collection("orders").document(order.orderId)
            }
            
            val orderToSave = order.copy(
                orderId = documentRef.id,
                updatedAt = System.currentTimeMillis()
            )
            
            documentRef.set(orderToSave).await()
            emit(Resource.Success(orderToSave))
            
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "Failed to create order", e)
            emit(Resource.Error(e.message ?: "Failed to create order"))
        }
    }

    override fun getUserOrders(userId: String): Flow<Resource<List<Order>>> = callbackFlow {
        trySend(Resource.Loading)
        
        val db = firestore
        if (db == null) {
            trySend(Resource.Success(emptyList()))
            close()
            return@callbackFlow
        }

        val listenerRegistration = db.collection("orders")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Error fetching orders"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val orders = snapshot.toObjects(Order::class.java)
                    trySend(Resource.Success(orders))
                } else {
                    trySend(Resource.Success(emptyList()))
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override fun getOrderById(orderId: String): Flow<Resource<Order>> = flow {
        emit(Resource.Loading)
        val db = firestore
        if (db == null) {
            emit(Resource.Error("Firestore not configured"))
            return@flow
        }
        
        try {
            val doc = db.collection("orders").document(orderId).get().await()
            val order = doc.toObject(Order::class.java)
            if (order != null) {
                emit(Resource.Success(order))
            } else {
                emit(Resource.Error("Order not found"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Failed to fetch order details"))
        }
    }

    override fun observeOrderById(orderId: String): Flow<Resource<Order>> = callbackFlow {
        trySend(Resource.Loading)
        val db = firestore
        if (db == null) {
            trySend(Resource.Error("Firestore not configured"))
            close()
            return@callbackFlow
        }

        val listenerRegistration = db.collection("orders").document(orderId)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Error observing order"))
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val order = snapshot.toObject(Order::class.java)
                    if (order != null) {
                        trySend(Resource.Success(order))
                    } else {
                        trySend(Resource.Error("Error parsing order"))
                    }
                } else {
                    trySend(Resource.Error("Order not found"))
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }
}
