package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.User
import com.foodfusionai.app.utils.Resource
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class AuthRepositoryImplTest {

    private lateinit var authRepository: AuthRepositoryImpl

    @Before
    fun setUp() {
        // Instantiate AuthRepositoryImpl with default parameter fallback (or null context)
        // In JVM test environment without Android runtime context, firebaseAuth lazy property safely falls back to null.
        authRepository = AuthRepositoryImpl()
    }

    @Test
    fun `test fallback login creates mock user and updates current user flow`() = runBlocking {
        assertFalse(authRepository.isLoggedIn())
        val result = authRepository.login("john@example.com", "Password123!")

        assertTrue(result is Resource.Success)
        val user = (result as Resource.Success).data
        assertEquals("john@example.com", user.email)
        assertEquals("John", user.displayName)
        assertTrue(user.uid.startsWith("mock_user_"))

        assertTrue(authRepository.isLoggedIn())
        val currentUser = authRepository.currentUser.first()
        assertEquals(user, currentUser)
    }

    @Test
    fun `test fallback registration creates mock user with custom display name`() = runBlocking {
        val result = authRepository.register("Jane Doe", "jane@example.com", "9876543210", "Password123!")

        assertTrue(result is Resource.Success)
        val user = (result as Resource.Success).data
        assertEquals("jane@example.com", user.email)
        assertEquals("Jane Doe", user.displayName)
        assertEquals("9876543210", user.phoneNumber)
        assertTrue(user.uid.startsWith("mock_user_"))

        assertTrue(authRepository.isLoggedIn())
    }

    @Test
    fun `test fallback password reset returns success`() = runBlocking {
        val result = authRepository.sendPasswordResetEmail("jane@example.com")
        assertTrue(result is Resource.Success)
    }

    @Test
    fun `test fallback logout clears user session`() = runBlocking {
        authRepository.login("test@example.com", "Password123!")
        assertTrue(authRepository.isLoggedIn())

        val logoutResult = authRepository.logout()
        assertTrue(logoutResult is Resource.Success)
        assertFalse(authRepository.isLoggedIn())
        val currentUser = authRepository.currentUser.first()
        assertNull(currentUser)
    }

    @Test
    fun `test exception mapping matrix via reflection`() {
        val mapMethod: Method = AuthRepositoryImpl::class.java.getDeclaredMethod(
            "mapAuthExceptionToResource",
            Throwable::class.java
        )
        mapMethod.isAccessible = true

        fun mapException(throwable: Throwable): Resource.Error {
            return mapMethod.invoke(authRepository, throwable) as Resource.Error
        }

        // 1. FirebaseAuthInvalidCredentialsException
        val invalidCreds = mapException(FirebaseAuthInvalidCredentialsException("INVALID_CODE", "Invalid credentials"))
        assertEquals("Invalid email or password. Please check your credentials and try again.", invalidCreds.message)

        // 2. FirebaseAuthInvalidUserException
        val invalidUser = mapException(FirebaseAuthInvalidUserException("USER_NOT_FOUND", "User not found"))
        assertEquals("No active account found with this email address, or the account has been disabled.", invalidUser.message)

        // 3. FirebaseAuthUserCollisionException
        val userCollision = mapException(FirebaseAuthUserCollisionException("EMAIL_EXISTS", "Email exists"))
        assertEquals("An account with this email address already exists. Please log in instead.", userCollision.message)

        // 4. FirebaseAuthWeakPasswordException
        val weakPassword = mapException(FirebaseAuthWeakPasswordException("WEAK_PASSWORD", "Weak password", "Reason"))
        assertEquals("Password is too weak. Please use a stronger password with a mix of letters, numbers, and symbols.", weakPassword.message)

        // 5. FirebaseNetworkException
        val networkEx = mapException(FirebaseNetworkException("Network connection failed"))
        assertEquals("Network error. Please check your internet connection and try again.", networkEx.message)

        // 6. FirebaseAuthActionCodeException
        val actionCodeEx = mapException(FirebaseAuthActionCodeException("EXPIRED_ACTION_CODE", "Action code expired"))
        assertEquals("The password reset link is invalid or has expired.", actionCodeEx.message)

        // 7. FirebaseAuthException - ERROR_TOO_MANY_REQUESTS
        val tooManyRequests = mapException(FirebaseAuthException("ERROR_TOO_MANY_REQUESTS", "Too many attempts"))
        assertEquals("Too many unsuccessful attempts. Please try again later.", tooManyRequests.message)

        // 8. FirebaseAuthException - ERROR_OPERATION_NOT_ALLOWED
        val opNotAllowed = mapException(FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "Disabled"))
        assertEquals("Email/password authentication is currently disabled.", opNotAllowed.message)

        // 9. FirebaseAuthException - generic code
        val otherAuthEx = mapException(FirebaseAuthException("UNKNOWN_CODE", "Custom auth error"))
        assertEquals("Custom auth error", otherAuthEx.message)

        // 10. IllegalStateException
        val illegalStateEx = mapException(IllegalStateException("Uninitialized"))
        assertEquals("Authentication service is currently unavailable.", illegalStateEx.message)

        // 11. Generic Throwable
        val genericEx = mapException(RuntimeException("Something went wrong"))
        assertEquals("Something went wrong", genericEx.message)

        // 12. CancellationException is rethrown
        try {
            mapException(kotlinx.coroutines.CancellationException("Job cancelled"))
            org.junit.Assert.fail("Expected CancellationException to be rethrown")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            assertTrue(e.cause is kotlinx.coroutines.CancellationException)
        }
    }
}
