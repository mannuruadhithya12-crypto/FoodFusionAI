package com.foodfusionai.app.data.local.room.dao

import androidx.room.*
import com.foodfusionai.app.data.local.room.entity.CachedFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedFoodDao {
    @Query("SELECT * FROM cached_foods")
    fun getAllCachedFoods(): Flow<List<CachedFoodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<CachedFoodEntity>)

    @Query("DELETE FROM cached_foods")
    suspend fun clearCachedFoods()
}
