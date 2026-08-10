package com.foodfusionai.driver.data.models

import androidx.annotation.Keep

@Keep
data class Driver(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val vehicleType: String = "",
    val vehicleNumber: String = "",
    val licenseNumber: String = "",
    val emergencyContact: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, SUSPENDED, REJECTED, DEACTIVATED
    val availability: String = "OFFLINE", // ONLINE, OFFLINE, BUSY
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val fcmToken: String? = null
)
