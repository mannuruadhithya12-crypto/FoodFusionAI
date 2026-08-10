package com.foodfusionai.driver.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val db = FirebaseFirestore.getInstance()
    private var currentOrderId: String? = null
    private var driverId: String? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocationInFirestore(location)
                }
            }
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val orderId = intent?.getStringExtra(EXTRA_ORDER_ID)
        val driverId = intent?.getStringExtra(EXTRA_DRIVER_ID)
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopTracking()
            stopSelf()
            return START_NOT_STICKY
        }

        if (orderId != null && driverId != null) {
            this.currentOrderId = orderId
            this.driverId = driverId
            startForegroundServiceCompat()
            startLocationUpdates()
        }

        return START_STICKY
    }

    private fun startForegroundServiceCompat() {
        val notification = createNotification("Tracking delivery live location...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 
            10000L // 10 seconds interval
        ).apply {
            setMinUpdateDistanceMeters(15f) // 15 meters throttling
            setMinUpdateIntervalMillis(5000L) // 5 seconds fastest interval
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationService", "Requested location updates for order $currentOrderId")
        } catch (e: Exception) {
            Log.e("LocationService", "Failed to start location updates", e)
        }
    }

    private fun updateLocationInFirestore(location: Location) {
        val orderId = currentOrderId ?: return
        val driverId = this.driverId ?: return

        serviceScope.launch {
            val locationData = hashMapOf(
                "driverId" to driverId,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "heading" to location.bearing,
                "speed" to location.speed,
                "accuracy" to location.accuracy,
                "updatedAt" to System.currentTimeMillis()
            )

            try {
                db.collection("deliveryLocations").document(orderId).set(locationData).await()
                Log.d("LocationService", "Updated location: ${location.latitude}, ${location.longitude}")
            } catch (e: Exception) {
                Log.e("LocationService", "Failed to update location in Firestore", e)
            }
        }
    }

    private fun stopTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            serviceScope.cancel()
            Log.d("LocationService", "Location updates stopped")
        } catch (e: Exception) {
            Log.e("LocationService", "Error stopping location updates", e)
        }
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FoodFusion Driver Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Delivery Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows delivery tracking notifications"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // Standard task-based await extensions for task await
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result, null)
            } else {
                cont.resumeWith(Result.failure(task.exception ?: Exception("Task failed")))
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_DRIVER_ID = "extra_driver_id"

        const val ACTION_STOP = "action_stop"

        fun start(context: Context, orderId: String, driverId: String) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_DRIVER_ID, driverId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
