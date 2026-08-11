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
import com.google.firebase.functions.FirebaseFunctions

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
        
        try {
            val functions = FirebaseFunctions.getInstance()
            val data = hashMapOf(
                "restaurantId" to order.restaurantId,
                "items" to order.items.map {
                    hashMapOf(
                        "foodId" to it.foodId,
                        "quantity" to it.quantity
                    )
                },
                "couponCode" to null, // Can be extended to read from order object if available
                "deliveryAddress" to hashMapOf(
                    "id" to (order.addressSnapshot?.id ?: ""),
                    "type" to (order.addressSnapshot?.type ?: "Home"),
                    "street" to (order.addressSnapshot?.street ?: ""),
                    "city" to (order.addressSnapshot?.city ?: "")
                )
            )
            
            val result = functions.getHttpsCallable("createOrder").call(data).await()
            val responseMap = result.data as? Map<String, Any>
            
            if (responseMap != null && responseMap.containsKey("orderId")) {
                val orderId = responseMap["orderId"] as String
                val createdOrder = order.copy(
                    orderId = orderId,
                    orderStatus = com.foodfusionai.app.data.models.order.OrderStatus.PENDING_PAYMENT,
                    paymentStatus = com.foodfusionai.app.data.models.order.PaymentStatus.PENDING
                )
                emit(Resource.Success(createdOrder))
            } else {
                emit(Resource.Error("Invalid response from server"))
            }
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "Failed to create order via function", e)
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
