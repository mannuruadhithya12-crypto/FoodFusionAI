package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.order.Order
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    /**
     * Creates an order atomically. Emits Loading, then Success(Order) or Error.
     * Implementing idempotency via reference/checkout checks if possible.
     */
    fun createOrder(order: Order): Flow<Resource<Order>>

    /**
     * Fetches all orders for the given user ID.
     */
    fun getUserOrders(userId: String): Flow<Resource<List<Order>>>

    /**
     * Fetches a single order by ID.
     */
    fun getOrderById(orderId: String): Flow<Resource<Order>>
}
