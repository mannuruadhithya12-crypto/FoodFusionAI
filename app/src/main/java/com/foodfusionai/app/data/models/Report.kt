package com.foodfusionai.app.data.models

data class Report(
    val reportId: String = "",
    val reviewId: String = "",
    val reporterId: String = "",
    val reason: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
