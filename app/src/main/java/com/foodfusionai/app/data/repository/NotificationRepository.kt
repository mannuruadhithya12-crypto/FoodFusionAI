package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Notification
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeNotifications(): Flow<Resource<List<Notification>>>
    suspend fun markAsRead(notificationId: String): Resource<Unit>
    suspend fun markAllAsRead(): Resource<Unit>
    suspend fun deleteNotification(notificationId: String): Resource<Unit>
}
