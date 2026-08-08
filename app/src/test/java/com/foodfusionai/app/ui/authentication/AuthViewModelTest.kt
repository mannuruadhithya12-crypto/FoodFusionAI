package com.foodfusionai.app.ui.authentication

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.foodfusionai.app.data.models.User
import com.foodfusionai.app.data.repository.AuthRepository
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var mockRepository: MockAuthRepository

    @Before
    fun setUp() {
        // Set up the ArchTaskExecutor delegate to run LiveData operations on the current JVM thread
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockRepository = MockAuthRepository()
        viewModel = AuthViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        // Clear the ArchTaskExecutor delegate
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun `test login validation with valid credentials`() {
        val isValid = viewModel.validateLogin("test@example.com", "Password123!")
        assertTrue(isValid)
        assertNull(viewModel.emailError.value)
        assertNull(viewModel.passwordError.value)
    }

    @Test
    fun `test login validation with invalid email`() {
        val isValid = viewModel.validateLogin("invalid-email", "Password123!")
        assertFalse(isValid)
        assertEquals("Please enter a valid email address", viewModel.emailError.value)
    }

    @Test
    fun `test login validation with empty password`() {
        val isValid = viewModel.validateLogin("test@example.com", "")
        assertFalse(isValid)
        assertEquals("Please enter your password", viewModel.passwordError.value)
    }

    @Test
    fun `test register validation with valid values`() {
        val isValid = viewModel.validateRegister(
            name = "John Doe",
            email = "john@example.com",
            phone = "9876543210",
            password = "Password123!",
            confirmPassword = "Password123!"
        )
        assertTrue(isValid)
        assertNull(viewModel.nameError.value)
        assertNull(viewModel.emailError.value)
        assertNull(viewModel.phoneError.value)
        assertNull(viewModel.passwordError.value)
        assertNull(viewModel.confirmPasswordError.value)
    }

    @Test
    fun `test register validation with empty fields`() {
        val isValid = viewModel.validateRegister(
            name = "",
            email = "",
            phone = "",
            password = "",
            confirmPassword = ""
        )
        assertFalse(isValid)
        assertEquals("Please enter your name", viewModel.nameError.value)
        assertEquals("Please enter a valid email address", viewModel.emailError.value)
        assertEquals("Please enter a valid 10-digit phone number", viewModel.phoneError.value)
        assertEquals("Password must be at least 8 characters, with 1 uppercase, 1 lowercase, 1 digit, and 1 special character", viewModel.passwordError.value)
        assertNull(viewModel.confirmPasswordError.value) // Not checked if passwords are both empty/invalid
    }

    @Test
    fun `test register validation with name too long`() {
        val longName = "A".repeat(51)
        val isValid = viewModel.validateRegister(
            name = longName,
            email = "john@example.com",
            phone = "9876543210",
            password = "Password123!",
            confirmPassword = "Password123!"
        )
        assertFalse(isValid)
        assertEquals("Name cannot exceed 50 characters", viewModel.nameError.value)
    }

    @Test
    fun `test register validation with password mismatch`() {
        val isValid = viewModel.validateRegister(
            name = "John Doe",
            email = "john@example.com",
            phone = "9876543210",
            password = "Password123!",
            confirmPassword = "Different123!"
        )
        assertFalse(isValid)
        assertEquals("Passwords do not match", viewModel.confirmPasswordError.value)
    }

    @Test
    fun `test forgot password validation with valid email`() {
        val isValid = viewModel.validateForgotPassword("test@example.com")
        assertTrue(isValid)
        assertNull(viewModel.emailError.value)
    }

    @Test
    fun `test forgot password validation with invalid email`() {
        val isValid = viewModel.validateForgotPassword("invalid-email")
        assertFalse(isValid)
        assertEquals("Please enter a valid email address", viewModel.emailError.value)
    }

    class MockAuthRepository : AuthRepository {
        override val currentUser: Flow<User?> = flowOf(null)
        override fun isLoggedIn(): Boolean = false
        override suspend fun login(email: String, password: String): Resource<User> = Resource.Success(User())
        override suspend fun register(name: String, email: String, phone: String, password: String): Resource<User> = Resource.Success(User())
        override suspend fun sendPasswordResetEmail(email: String): Resource<Unit> = Resource.Success(Unit)
        override suspend fun logout(): Resource<Unit> = Resource.Success(Unit)
    }
}
