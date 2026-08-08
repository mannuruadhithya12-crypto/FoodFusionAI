package com.foodfusionai.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cart_items")
data class CartEntity(
    @PrimaryKey
    val id: String,
    val foodId: String,
    val foodName: String,
    val price: Double,
    val quantity: Int,
    val imageUrl: String,
    val customizationsJson: String? = null,
    val restaurantId: String = ""
)
