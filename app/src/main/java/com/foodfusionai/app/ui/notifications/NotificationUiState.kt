package com.foodfusionai.app.ui.notifications

import com.foodfusionai.app.data.models.Notification

data class NotificationUiState(
    val notifications: List<Notification> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)
