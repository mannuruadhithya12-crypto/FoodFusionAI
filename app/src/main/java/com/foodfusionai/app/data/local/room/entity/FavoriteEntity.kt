package com.foodfusionai.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val id: String,
    val foodId: String,
    val restaurantId: String,
    val foodName: String,
    val imageUrl: String,
    val price: Double
)
