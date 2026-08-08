package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.local.room.entity.CartEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface contract for Shopping Cart data operations.
 */
interface CartRepository {

    /**
     * Obtains an observable flow of all cart items in the database.
     */
    fun getAllCartItems(): Flow<List<CartEntity>>

    /**
     * Adds an item to the shopping cart, updating its quantity if it already exists.
     */
    suspend fun addToCart(item: CartEntity)

    /**
     * Removes an item from the cart.
     */
    suspend fun removeFromCart(item: CartEntity)

    /**
     * Clears all items in the cart.
     */
    suspend fun clearCart()
}
