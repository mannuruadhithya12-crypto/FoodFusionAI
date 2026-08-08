package com.foodfusionai.app.data.repository

import android.content.Context
import com.foodfusionai.app.FoodFusionApp
import com.foodfusionai.app.data.local.room.FoodFusionDatabase
import com.foodfusionai.app.data.local.room.entity.CartEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [CartRepository] managing cart state off-thread.
 */
class CartRepositoryImpl(
    private val context: Context? = try { FoodFusionApp.instance } catch (_: Throwable) { null }
) : CartRepository {

    private val db: FoodFusionDatabase? by lazy {
        context?.let { FoodFusionDatabase.getDatabase(it) }
    }

    override fun getAllCartItems(): Flow<List<CartEntity>> {
        val dao = db?.cartDao()
        return dao?.getAllCartItems() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    override suspend fun addToCart(item: CartEntity) = withContext(Dispatchers.IO) {
        val dao = db?.cartDao() ?: return@withContext
        
        // Retrieve current items to check for matching duplicate food items and customizations
        val currentItems = dao.getAllCartItems().first()
        val existingItem = currentItems.find { 
            it.foodId == item.foodId && it.customizationsJson == item.customizationsJson 
        }

        if (existingItem != null) {
            val updated = existingItem.copy(quantity = existingItem.quantity + item.quantity)
            dao.updateCartItem(updated)
        } else {
            dao.insertCartItem(item)
        }
    }

    override suspend fun removeFromCart(item: CartEntity) = withContext(Dispatchers.IO) {
        val dao = db?.cartDao() ?: return@withContext
        dao.deleteCartItem(item)
    }

    override suspend fun clearCart() = withContext(Dispatchers.IO) {
        val dao = db?.cartDao() ?: return@withContext
        dao.clearCart()
    }
}
