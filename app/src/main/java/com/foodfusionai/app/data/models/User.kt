package com.foodfusionai.app.data.models

import androidx.annotation.Keep

/**
 * Data model representing an authenticated user in FoodFusion AI.
 *
 * Annotated with @Keep to prevent R8/ProGuard obfuscation in release builds.
 * Default values provide a zero-argument constructor required for Firebase Firestore deserialization.
 */
@Keep
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
