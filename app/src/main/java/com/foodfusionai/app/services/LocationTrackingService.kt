package com.foodfusionai.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.foodfusionai.app.R
import com.foodfusionai.app.data.location.LocationProvider
import com.foodfusionai.app.data.repository.DriverLocationRepository
import com.foodfusionai.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Foreground Service for driver GPS tracking during active deliveries.
 *
 * ── Part AB: Location lifecycle ───────────────────────────────────────────────
 *   START_TRACKING intent  → GPS collection begins
 *   STOP_TRACKING intent   → GPS collection stops, service self-stops
 *
 * ── Part AC: Background location ─────────────────────────────────────────────
 *   Runs as a foreground service with a persistent notification so the OS
 *   does not kill it during delivery.  Uses `foregroundServiceType="location"`
 *   (declared in AndroidManifest.xml) for Android 10+ compliance.
 *
 * ── Battery conscious ─────────────────────────────────────────────────────────
 *   Uses [LocationProvider.Mode.DELIVERY] (7 s interval, 10 m distance threshold).
 *   Tracking is ONLY active while an order is in progress — never all day.
 *
 * ── Privacy ───────────────────────────────────────────────────────────────────
 *   Location is written via the `updateDriverLocation` Cloud Function, which
 *   validates ownership.  Drivers cannot write another driver's location.
 *   The document is deactivated on STOP_TRACKING so customers lose visibility.
 */
class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trackingJob: Job? = null

    private lateinit var locationProvider: LocationProvider
    private lateinit var locationRepository: DriverLocationRepository

    private var currentOrderId: String? = null

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        locationProvider    = LocationProvider(applicationContext)
        locationRepository  = DriverLocationRepository()

        createNotificationChannel()
        Log.d(TAG, "LocationTrackingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: return START_NOT_STICKY
                startTracking(orderId)
            }
            ACTION_STOP_TRACKING -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "LocationTrackingService destroyed")
    }

    // ── Tracking control ──────────────────────────────────────────────────────

    private fun startTracking(orderId: String) {
        if (trackingJob?.isActive == true && currentOrderId == orderId) {
            Log.d(TAG, "Already tracking order $orderId")
            return
        }

        currentOrderId = orderId
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "Starting GPS tracking for order $orderId")

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            try {
                locationProvider.locationUpdates(LocationProvider.Mode.DELIVERY)
                    .collect { locationResult ->
                        Log.v(TAG, "GPS fix: ${locationResult.point} acc=${locationResult.accuracy}m")
                        locationRepository.writeLocation(orderId, locationResult)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Location tracking error for order $orderId: ${e.message}")
                // Service continues running — will retry on next GPS fix
            }
        }
    }

    private fun stopTracking() {
        val orderId = currentOrderId
        Log.d(TAG, "Stopping GPS tracking for order $orderId")

        trackingJob?.cancel()
        trackingJob = null

        if (orderId != null) {
            // Deactivate the Firestore document so customers lose live GPS access
            serviceScope.launch {
                locationRepository.deactivateTracking(orderId)
            }
        }

        currentOrderId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Delivery Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active delivery location tracking"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Delivering your order")
        .setContentText("Your location is being shared with the customer")
        .setSmallIcon(R.drawable.ic_location)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "LocationTrackingService"

        const val ACTION_START_TRACKING = "com.foodfusionai.app.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING  = "com.foodfusionai.app.ACTION_STOP_TRACKING"
        const val EXTRA_ORDER_ID        = "extra_order_id"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID      = "delivery_tracking_channel"

        /**
         * Convenience: start tracking for [orderId].
         * Call from the ViewModel when order transitions to OUT_FOR_DELIVERY.
         */
        fun startTracking(context: Context, orderId: String) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_ORDER_ID, orderId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Convenience: stop tracking and deactivate the Firestore document. */
        fun stopTracking(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
    }
}
