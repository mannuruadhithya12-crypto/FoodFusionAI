package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.User
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Interface contract for authentication and session management operations in FoodFusion AI.
 * Wraps Firebase Auth and local session persistence abstractions.
 */
interface AuthRepository {

    /**
     * Observable reactive stream of the currently authenticated [User], or null if signed out.
     */
    val currentUser: Flow<User?>

    /**
     * Authenticates a user with email and password credentials.
     *
     * @param email User's email address
     * @param password User's account password
     * @return [Resource.Success] with [User], or [Resource.Error] on failure
     */
    suspend fun login(email: String, password: String): Resource<User>

    /**
     * Registers a new user account with display name, email, and password.
     *
     * @param name User's full display name
     * @param email User's email address
     * @param phone User's phone number
     * @param password Account password
     * @return [Resource.Success] with created [User], or [Resource.Error] on failure
     */
    suspend fun register(name: String, email: String, phone: String, password: String): Resource<User>

    /**
     * Triggers a password reset email via Firebase Auth.
     *
     * @param email Email address to send the reset link to
     * @return [Resource.Success] on success, or [Resource.Error] on failure
     */
    suspend fun sendPasswordResetEmail(email: String): Resource<Unit>

    /**
     * Logs out the active user session.
     *
     * @return [Resource.Success] on completion, or [Resource.Error] on failure
     */
    suspend fun logout(): Resource<Unit>

    /**
     * Synchronously checks if a valid user session is active.
     *
     * @return true if logged in, false otherwise
     */
    fun isLoggedIn(): Boolean
}
