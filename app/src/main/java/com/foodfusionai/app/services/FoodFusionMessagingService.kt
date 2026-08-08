package com.foodfusionai.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.foodfusionai.app.ui.MainActivity
import com.foodfusionai.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FoodFusionMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to server if needed
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        val type = message.data["type"]
        val targetId = message.data["targetId"] // Could be orderId, couponId, restaurantId
        
        message.notification?.let {
            showNotification(it.title, it.body, type, targetId)
        }
    }

    private fun showNotification(title: String?, body: String?, type: String?, targetId: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        if (type != null && targetId != null) {
            intent.putExtra("deep_link_type", type)
            intent.putExtra("deep_link_target_id", targetId)
        } else if (targetId != null) {
            // Fallback for older notifications that just sent orderId
            intent.putExtra("deep_link_order_id", targetId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, (System.currentTimeMillis() % 10000).toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "food_fusion_notifications"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Fallback icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Food Fusion Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}
