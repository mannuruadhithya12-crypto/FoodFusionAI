package com.foodfusionai.app.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.foodfusionai.app.data.local.room.dao.CachedFoodDao
import com.foodfusionai.app.data.local.room.dao.CartDao
import com.foodfusionai.app.data.local.room.dao.FavoriteDao
import com.foodfusionai.app.data.local.room.dao.RecentSearchDao
import com.foodfusionai.app.data.local.room.entity.CachedFoodEntity
import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.local.room.entity.FavoriteEntity
import com.foodfusionai.app.data.local.room.entity.RecentSearchEntity

@Database(
    entities = [
        CartEntity::class,
        FavoriteEntity::class,
        RecentSearchEntity::class,
        CachedFoodEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FoodFusionDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun cachedFoodDao(): CachedFoodDao

    companion object {
        @Volatile
        private var INSTANCE: FoodFusionDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cart_items ADD COLUMN restaurantId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: android.content.Context): FoodFusionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    FoodFusionDatabase::class.java,
                    "food_fusion_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
