package com.foodfusionai.app.data.repository

import com.foodfusionai.app.utils.Resource
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class StorageRepositoryImplTest {

    private lateinit var storageRepository: StorageRepositoryImpl

    @Before
    fun setUp() {
        storageRepository = StorageRepositoryImpl()
    }

    @Test
    fun `test fallback profile image upload returns mock url`() = runBlocking {
        val result = storageRepository.uploadProfileImage("user_123", null)
        assertTrue(result is Resource.Success)
        assertEquals("https://mock.foodfusion.ai/users/user_123/profile.jpg", (result as Resource.Success).data)
    }

    @Test
    fun `test fallback restaurant image upload returns mock url`() = runBlocking {
        val result = storageRepository.uploadRestaurantImage("res_abc", null)
        assertTrue(result is Resource.Success)
        assertEquals("https://mock.foodfusion.ai/restaurants/res_abc/image.jpg", (result as Resource.Success).data)
    }

    @Test
    fun `test fallback food image upload returns mock url`() = runBlocking {
        val result = storageRepository.uploadFoodImage("food_999", null)
        assertTrue(result is Resource.Success)
        assertEquals("https://mock.foodfusion.ai/foods/food_999/image.jpg", (result as Resource.Success).data)
    }

    @Test
    fun `test fallback download url returns mock url`() = runBlocking {
        val result = storageRepository.getDownloadUrl("banners/summer.jpg")
        assertTrue(result is Resource.Success)
        assertEquals("https://mock.foodfusion.ai/banners/summer.jpg", (result as Resource.Success).data)
    }

    @Test
    fun `test storage exception mapping matrix via reflection`() {
        val mapMethod: Method = StorageRepositoryImpl::class.java.getDeclaredMethod(
            "mapStorageException",
            Throwable::class.java
        )
        mapMethod.isAccessible = true

        fun mapException(throwable: Throwable): Resource.Error {
            return mapMethod.invoke(storageRepository, throwable) as Resource.Error
        }

        // Test with different StorageException error codes using the reflection helper
        
        // 1. ERROR_OBJECT_NOT_FOUND
        val objectNotFound = mapException(createStorageException(StorageException.ERROR_OBJECT_NOT_FOUND))
        assertEquals("The requested image does not exist.", objectNotFound.message)

        // 2. ERROR_BUCKET_NOT_FOUND
        val bucketNotFound = mapException(createStorageException(StorageException.ERROR_BUCKET_NOT_FOUND))
        assertEquals("Storage bucket configuration is missing.", bucketNotFound.message)

        // 3. ERROR_NOT_AUTHORIZED
        val notAuthorized = mapException(createStorageException(StorageException.ERROR_NOT_AUTHORIZED))
        assertEquals("You do not have permission to perform this storage action.", notAuthorized.message)

        // 4. ERROR_RETRY_LIMIT_EXCEEDED
        val limitExceeded = mapException(createStorageException(StorageException.ERROR_RETRY_LIMIT_EXCEEDED))
        assertEquals("The operation timed out. Please try again.", limitExceeded.message)

        // 5. Generic Throwable
        val genericEx = mapException(RuntimeException("Storage failure"))
        assertEquals("Storage failure", genericEx.message)
    }

    /**
     * Reflection helper to instantiate StorageException whose constructor is package-private in the Firebase SDK.
     */
    private fun createStorageException(errorCode: Int): StorageException {
        val constructor = StorageException::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            Throwable::class.java,
            Int::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        return constructor.newInstance(errorCode, null, 0)
    }
}
