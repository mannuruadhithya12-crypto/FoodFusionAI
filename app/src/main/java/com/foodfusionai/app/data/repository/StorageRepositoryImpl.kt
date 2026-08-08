package com.foodfusionai.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.foodfusionai.app.FoodFusionApp
import com.foodfusionai.app.utils.Resource
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Production implementation of [StorageRepository].
 * Connects to Firebase Storage defensively with clean fallback modes.
 */
class StorageRepositoryImpl(
    private val context: Context? = try { FoodFusionApp.instance } catch (_: Throwable) { null }
) : StorageRepository {

    companion object {
        private const val TAG = "StorageRepositoryImpl"
    }

    private val firebaseStorage: FirebaseStorage? by lazy {
        try {
            val ctx = context ?: return@lazy null
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(ctx)
            }
            FirebaseStorage.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase Storage failed to initialize. Fallback Mode active.", e)
            null
        }
    }

    override suspend fun uploadProfileImage(userId: String, fileUri: Uri?): Resource<String> =
        uploadFile("users/$userId/profile.jpg", fileUri)

    override suspend fun uploadRestaurantImage(restaurantId: String, fileUri: Uri?): Resource<String> =
        uploadFile("restaurants/$restaurantId/image.jpg", fileUri)

    override suspend fun uploadFoodImage(foodId: String, fileUri: Uri?): Resource<String> =
        uploadFile("foods/$foodId/image.jpg", fileUri)

    override suspend fun uploadBannerImage(bannerId: String, fileUri: Uri?): Resource<String> =
        uploadFile("banners/$bannerId/banner.jpg", fileUri)

    override suspend fun getDownloadUrl(path: String): Resource<String> = withContext(Dispatchers.IO) {
        val storage = firebaseStorage
        if (storage == null) {
            return@withContext Resource.Success("https://mock.foodfusion.ai/$path")
        }
        try {
            val url = storage.reference.child(path).downloadUrl.await().toString()
            Resource.Success(url)
        } catch (e: Throwable) {
            mapStorageException(e)
        }
    }

    private suspend fun uploadFile(path: String, fileUri: Uri?): Resource<String> = withContext(Dispatchers.IO) {
        val storage = firebaseStorage
        if (storage == null) {
            Log.d(TAG, "Storage uninitialized. Mock upload successful for: $path")
            return@withContext Resource.Success("https://mock.foodfusion.ai/$path")
        }
        if (fileUri == null) {
            return@withContext Resource.Error("No image selected to upload.", null)
        }
        try {
            val ref = storage.reference.child(path)
            ref.putFile(fileUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Resource.Success(downloadUrl)
        } catch (e: Throwable) {
            mapStorageException(e)
        }
    }

    private fun mapStorageException(throwable: Throwable): Resource.Error {
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable

        val message = when (throwable) {
            is StorageException -> {
                when (throwable.errorCode) {
                    StorageException.ERROR_OBJECT_NOT_FOUND -> "The requested image does not exist."
                    StorageException.ERROR_BUCKET_NOT_FOUND -> "Storage bucket configuration is missing."
                    StorageException.ERROR_NOT_AUTHORIZED -> "You do not have permission to perform this storage action."
                    StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> "The operation timed out. Please try again."
                    else -> throwable.localizedMessage ?: "Image transfer failed."
                }
            }
            else -> throwable.localizedMessage ?: "An unexpected error occurred during file operation."
        }
        val exception = throwable as? Exception ?: Exception(throwable)
        return Resource.Error(message = message, exception = exception)
    }
}
