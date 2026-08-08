package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.User
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    /**
     * Observers the current user profile from Firestore.
     */
    fun observeProfile(): Flow<Resource<User>>

    /**
     * Updates the user profile fields.
     */
    suspend fun updateProfile(displayName: String, phoneNumber: String): Resource<Unit>

    /**
     * Safely logs out the user and clears any local user-scoped state.
     */
    suspend fun logout(): Resource<Unit>

    /**
     * Deletes the user account completely via backend function.
     */
    suspend fun deleteAccount(): Resource<Unit>
}
