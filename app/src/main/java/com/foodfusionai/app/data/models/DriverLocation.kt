package com.foodfusionai.app.data.models

/**
 * Real-time GPS snapshot for an active delivery driver.
 *
 * Written by the driver's foreground service via the `updateDriverLocation`
 * Cloud Function.  Read by the customer tracking screen via a scoped Firestore
 * listener on `deliveryLocations/{orderId}`.
 *
 * Security: customers may only read the document for their own active order.
 * The document is soft-deleted (set `isActive = false`) when the order reaches
 * DELIVERED or CANCELLED so the live GPS is no longer exposed.
 *
 * Firestore path: `deliveryLocations/{orderId}`
 */
data class DriverLocation(
    val orderId: String = "",
    val driverId: String = "",

    // ── GPS fix ───────────────────────────────────────────────────────────────
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** Accuracy of the GPS fix in metres; lower is better. */
    val accuracy: Float = 0f,
    /** Bearing in degrees [0, 360); null/0 when stationary. */
    val heading: Float = 0f,
    /** Speed in m/s; 0 when stationary. */
    val speed: Float = 0f,

    // ── Timestamps ────────────────────────────────────────────────────────────
    /** Client device time when the fix was captured (millis since epoch). */
    val timestamp: Long = 0L,
    /** Firestore server timestamp (written as FieldValue.serverTimestamp()). */
    val updatedAt: Long = 0L,

    // ── Status ────────────────────────────────────────────────────────────────
    /** False after delivery is completed/cancelled — stops live tracking exposure. */
    val isActive: Boolean = true,
    /**
     * Spoofing alert flag set by the `flagSuspiciousLocation` Cloud Function
     * when an impossible-speed jump is detected.  Operational alert only —
     * does not auto-ban the driver.
     */
    val suspiciousMovementFlag: Boolean = false
)
