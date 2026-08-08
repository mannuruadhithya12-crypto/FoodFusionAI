package com.foodfusionai.app.data.models

data class Notification(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "", // e.g. ORDER_UPDATE, PROMO, ALERT
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val data: Map<String, String> = emptyMap()
)
