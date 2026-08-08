package com.foodfusionai.app.data.repository

import android.net.Uri
import com.foodfusionai.app.utils.Resource

/**
 * Interface contract for Firebase Storage operations.
 * Manages uploading and fetching media resources (profile, restaurant, food images, banners).
 */
interface StorageRepository {

    /**
     * Uploads a profile image for a specific user.
     * @return [Resource.Success] with the public download URL, or [Resource.Error]
     */
    suspend fun uploadProfileImage(userId: String, fileUri: Uri?): Resource<String>

    /**
     * Uploads an image for a specific restaurant.
     * @return [Resource.Success] with the public download URL, or [Resource.Error]
     */
    suspend fun uploadRestaurantImage(restaurantId: String, fileUri: Uri?): Resource<String>

    /**
     * Uploads an image for a specific food item.
     * @return [Resource.Success] with the public download URL, or [Resource.Error]
     */
    suspend fun uploadFoodImage(foodId: String, fileUri: Uri?): Resource<String>

    /**
     * Uploads a promotional banner image.
     * @return [Resource.Success] with the public download URL, or [Resource.Error]
     */
    suspend fun uploadBannerImage(bannerId: String, fileUri: Uri?): Resource<String>

    /**
     * Fetches the download URL for a given storage reference path.
     * @return [Resource.Success] with download URL, or [Resource.Error]
     */
    suspend fun getDownloadUrl(path: String): Resource<String>
}
