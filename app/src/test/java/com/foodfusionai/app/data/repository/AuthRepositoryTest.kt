package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.User
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun testUserModelDefaults() {
        val user = User()
        assertEquals("", user.uid)
        assertEquals("", user.email)
        assertEquals("", user.displayName)
        assertEquals("", user.phoneNumber)
        assertNull(user.photoUrl)
        assertTrue(user.createdAt > 0)
    }

    @Test
    fun testUserModelCustomValues() {
        val user = User(
            uid = "u123",
            email = "test@foodfusion.ai",
            displayName = "Chef Mario",
            phoneNumber = "9876543210",
            photoUrl = "https://example.com/avatar.png",
            createdAt = 1000L
        )
        assertEquals("u123", user.uid)
        assertEquals("test@foodfusion.ai", user.email)
        assertEquals("Chef Mario", user.displayName)
        assertEquals("9876543210", user.phoneNumber)
        assertEquals("https://example.com/avatar.png", user.photoUrl)
        assertEquals(1000L, user.createdAt)
    }
}
