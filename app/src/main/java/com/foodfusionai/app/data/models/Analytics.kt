package com.foodfusionai.app.data.models

data class Analytics(
    val id: String = "",
    val eventName: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val properties: Map<String, String> = emptyMap()
)
