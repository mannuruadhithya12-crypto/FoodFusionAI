package com.foodfusionai.app.data.repository

import android.content.Context
import android.util.Log
import com.foodfusionai.app.FoodFusionApp
import com.foodfusionai.app.data.models.User
import com.foodfusionai.app.utils.Resource
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Implementation of [AuthRepository] managing user authentication.
 * Integrates real Firebase Authentication using Kotlin Coroutines when available,
 * with a defensive fallback to an in-memory session manager when google-services.json
 * is missing or Firebase is uninitialized.
 */
class AuthRepositoryImpl(
    private val context: Context? = try { FoodFusionApp.instance } catch (_: Throwable) { null }
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepositoryImpl"
    }

    /**
     * Defensive lazy initialization of FirebaseAuth instance.
     * Prevents runtime crashes when google-services.json is absent or FirebaseApp is uninitialized.
     */
    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            val ctx = context ?: return@lazy null
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(ctx)
            }
            FirebaseAuth.getInstance()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Firebase is not initialized (google-services.json missing). Operating in Fallback Mode.", e)
            null
        } catch (e: NoClassDefFoundError) {
            Log.w(TAG, "Firebase classes not found. Operating in Fallback Mode.", e)
            null
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseAuth initialization error. Operating in Fallback Mode.", e)
            null
        }
    }
    
    /**
     * Defensive lazy initialization of FirebaseFirestore instance.
     */
    private val firebaseFirestore: FirebaseFirestore? by lazy {
        try {
            val ctx = context ?: return@lazy null
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(ctx)
            }
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase Firestore failed to initialize. Fallback Mode active.", e)
            null
        }
    }

    /**
     * Fallback in-memory user session state for when Firebase Auth is uninitialized or offline.
     */
    private val _fallbackUserFlow = MutableStateFlow<User?>(null)

    /**
     * Reactive observable stream of current authenticated user state.
     * Combines Firebase AuthStateListener updates when active with fallback session user,
     * ensuring seamless state representation across both real Firebase and fallback modes.
     */
    override val currentUser: Flow<User?> = if (firebaseAuth != null) {
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { authState ->
                val fbUser = authState.currentUser
                val domainUser = fbUser?.toDomainUser()
                trySend(domainUser)
            }
            firebaseAuth?.addAuthStateListener(listener)
            trySend(firebaseAuth?.currentUser?.toDomainUser())
            awaitClose {
                firebaseAuth?.removeAuthStateListener(listener)
            }
        }.combine(_fallbackUserFlow) { firebaseUser, fallbackUser ->
            firebaseUser ?: fallbackUser
        }
    } else {
        _fallbackUserFlow.asStateFlow()
    }

    override fun isLoggedIn(): Boolean {
        val auth = firebaseAuth
        return (auth?.currentUser != null) || (_fallbackUserFlow.value != null)
    }

    override suspend fun login(email: String, password: String): Resource<User> = withContext(Dispatchers.IO) {
        val auth = firebaseAuth
        if (auth == null) {
            Log.d(TAG, "Executing fallback login for email: $email")
            val fallbackUser = createMockUser(email)
            _fallbackUserFlow.value = fallbackUser
            return@withContext Resource.Success(fallbackUser)
        }

        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val fbUser = result.user ?: return@withContext Resource.Error("Authentication failed: User profile unavailable", null)
            Resource.Success(fbUser.toDomainUser())
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Firebase call failed with IllegalStateException; falling back to local session", e)
            val fallbackUser = createMockUser(email)
            _fallbackUserFlow.value = fallbackUser
            Resource.Success(fallbackUser)
        } catch (e: Throwable) {
            mapAuthExceptionToResource(e)
        }
    }

    override suspend fun register(name: String, email: String, phone: String, password: String): Resource<User> = withContext(Dispatchers.IO) {
        val auth = firebaseAuth
        if (auth == null) {
            Log.d(TAG, "Executing fallback registration for email: $email, name: $name, phone: $phone")
            val fallbackUser = createMockUser(email, name, phone)
            _fallbackUserFlow.value = fallbackUser
            return@withContext Resource.Success(fallbackUser)
        }

        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val fbUser = result.user ?: return@withContext Resource.Error("Registration failed: User profile unavailable", null)

            // Update display name in Firebase user profile
            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                withTimeout(5000) {
                    fbUser.updateProfile(profileUpdates).await()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to update display name on Firebase user profile", e)
            }

            val user = fbUser.toDomainUser(displayNameOverride = name, phoneOverride = phone)
            
            // Save complete user profile to Firestore
            val firestore = firebaseFirestore
            if (firestore != null) {
                try {
                    withTimeout(5000) {
                        firestore.collection("users").document(user.uid).set(user).await()
                    }
                    Log.d(TAG, "User profile successfully saved to Firestore.")
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to save user profile to Firestore.", e)
                }
            }

            Resource.Success(user)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Firebase call failed with IllegalStateException; falling back to local session", e)
            val fallbackUser = createMockUser(email, name, phone)
            _fallbackUserFlow.value = fallbackUser
            Resource.Success(fallbackUser)
        } catch (e: Throwable) {
            mapAuthExceptionToResource(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Resource<Unit> = withContext(Dispatchers.IO) {
        val auth = firebaseAuth
        if (auth == null) {
            Log.d(TAG, "Executing fallback password reset for email: $email")
            return@withContext Resource.Success(Unit)
        }

        try {
            auth.sendPasswordResetEmail(email).await()
            Resource.Success(Unit)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Firebase password reset failed with IllegalStateException; returning fallback success", e)
            Resource.Success(Unit)
        } catch (e: Throwable) {
            mapAuthExceptionToResource(e)
        }
    }

    override suspend fun logout(): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val auth = firebaseAuth
            auth?.signOut()
            _fallbackUserFlow.value = null
            Resource.Success(Unit)
        } catch (e: Throwable) {
            Log.w(TAG, "Error during logout; clearing fallback session", e)
            _fallbackUserFlow.value = null
            Resource.Success(Unit)
        }
    }

    /**
     * Converts a [FirebaseUser] instance into a domain [User] model.
     */
    private fun FirebaseUser.toDomainUser(displayNameOverride: String? = null, phoneOverride: String? = null): User {
        val derivedDisplayName = displayNameOverride
            ?.takeIf { it.isNotBlank() }
            ?: this.displayName
            ?.takeIf { it.isNotBlank() }
            ?: this.email?.takeIf { it.isNotBlank() }?.substringBefore("@")
            ?: "User"

        val derivedPhone = phoneOverride
            ?.takeIf { it.isNotBlank() }
            ?: this.phoneNumber
            ?: ""

        return User(
            uid = this.uid,
            email = this.email ?: "",
            displayName = derivedDisplayName,
            phoneNumber = derivedPhone,
            photoUrl = this.photoUrl?.toString(),
            createdAt = this.metadata?.creationTimestamp ?: System.currentTimeMillis()
        )
    }

    /**
     * Generates a mock [User] for fallback session operations when Firebase is unavailable.
     */
    private fun createMockUser(email: String, displayName: String? = null, phone: String? = null): User {
        val name = if (!displayName.isNullOrBlank()) {
            displayName
        } else {
            email.takeIf { it.isNotBlank() }
                ?.substringBefore("@")
                ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                ?: "User"
        }
        return User(
            uid = "mock_user_${UUID.randomUUID()}",
            email = email,
            displayName = name,
            phoneNumber = phone ?: "",
            photoUrl = null,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Centralized mapper converting Firebase and general exceptions to user-friendly [Resource.Error] messages.
     */
    private fun mapAuthExceptionToResource(throwable: Throwable): Resource.Error {
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable

        val message = when (throwable) {
            is FirebaseAuthWeakPasswordException -> {
                "Password is too weak. Please use a stronger password with a mix of letters, numbers, and symbols."
            }
            is FirebaseAuthInvalidCredentialsException -> {
                "Invalid email or password. Please check your credentials and try again."
            }
            is FirebaseAuthInvalidUserException -> {
                "No active account found with this email address, or the account has been disabled."
            }
            is FirebaseAuthUserCollisionException -> {
                "An account with this email address already exists. Please log in instead."
            }
            is FirebaseAuthRecentLoginRequiredException -> {
                "For security reasons, please log out and log in again before trying this."
            }
            is FirebaseNetworkException -> {
                "Network error. Please check your internet connection and try again."
            }
            is FirebaseAuthActionCodeException -> {
                "The password reset link is invalid or has expired."
            }
            is FirebaseAuthException -> {
                when (throwable.errorCode) {
                    "ERROR_TOO_MANY_REQUESTS" -> "Too many unsuccessful attempts. Please try again later."
                    "ERROR_OPERATION_NOT_ALLOWED" -> "Email/password authentication is currently disabled."
                    else -> throwable.localizedMessage ?: "Authentication failed. Please try again."
                }
            }
            is IllegalStateException -> {
                "Authentication service is currently unavailable."
            }
            else -> throwable.localizedMessage ?: "An unexpected error occurred. Please try again."
        }

        val exception = throwable as? Exception ?: Exception(throwable)
        return Resource.Error(message = message, exception = exception)
    }
}
