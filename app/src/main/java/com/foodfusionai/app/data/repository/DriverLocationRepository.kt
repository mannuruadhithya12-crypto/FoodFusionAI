package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.location.LocationResult
import com.foodfusionai.app.data.models.DriverLocation
import com.foodfusionai.app.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for driver GPS operations.
 *
 * Write path (driver side):
 *   [writeLocation] → `updateDriverLocation` Cloud Function
 *
 * The function validates:
 *   - Authentication
 *   - Ownership (driver can only update their own order's location)
 *   - Rate limiting (max one write per 5 seconds enforced server-side)
 *   - Coordinate bounds (lat ∈ [-90,90], lon ∈ [-180,180])
 *   - GPS accuracy (rejects fixes worse than 150 m)
 *   - Impossible speed detection → sets `suspiciousMovementFlag`
 *
 * Read path (customer side):
 *   [observeDriverLocation] → Firestore real-time listener on
 *   `deliveryLocations/{orderId}` (scoped — customer reads only their own order).
 *
 * Part P: FusedLocationProviderClient driver tracking
 * Part Q: driver cannot write another driver's location (Cloud Function enforces)
 * Part R: GPS data model
 */
class DriverLocationRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Sends a GPS fix to the backend via the `updateDriverLocation` Cloud Function.
     *
     * The backend writes to `deliveryLocations/{orderId}` and performs all
     * security checks.  Clients never write directly to Firestore for GPS.
     *
     * [Part AM] Accuracy handling: fixes worse than [MAX_ACCEPTABLE_ACCURACY_M] are
     * dropped client-side before calling the function to reduce wasted quota.
     *
     * [Part AN] Network recovery: if the function call fails, we silently drop
     * the update rather than queuing it, since a slightly stale location is
     * preferable to a burst of replayed historical fixes on reconnect.
     */
    suspend fun writeLocation(
        orderId: String,
        locationResult: LocationResult
    ): Resource<Unit> {
        // Client-side accuracy gate — don't send fixes worse than 150 m
        if (locationResult.accuracy > MAX_ACCEPTABLE_ACCURACY_M) {
            return Resource.Error("GPS fix accuracy too low (${locationResult.accuracy.toInt()} m), skipped")
        }

        return try {
            val data = hashMapOf(
                "orderId"   to orderId,
                "latitude"  to locationResult.point.latitude,
                "longitude" to locationResult.point.longitude,
                "accuracy"  to locationResult.accuracy,
                "heading"   to (locationResult.heading ?: 0f),
                "speed"     to (locationResult.speed ?: 0f),
                "timestamp" to locationResult.timestamp
            )

            functions.getHttpsCallable("updateDriverLocation").call(data).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            // Non-fatal — caller (LocationTrackingService) continues tracking
            Resource.Error(e.message ?: "Failed to update driver location")
        }
    }

    /**
     * Observes the live driver location for a customer's active order.
     *
     * Returns [Resource.Error] immediately when the document is inactive
     * (delivery completed/cancelled) so the UI can stop showing live data.
     *
     * [Part AD] Privacy: the customer can only read this document when their
     * `userId` matches the order's `userId` (enforced in Firestore rules).
     */
    fun observeDriverLocation(orderId: String): Flow<Resource<DriverLocation>> = callbackFlow {
        trySend(Resource.Loading)

        val ref = firestore.collection("deliveryLocations").document(orderId)

        val registration = ref.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error(error.message ?: "Failed to observe driver location"))
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                trySend(Resource.Empty)
                return@addSnapshotListener
            }

            val location = snapshot.toObject(DriverLocation::class.java)
            if (location != null) {
                if (!location.isActive) {
                    // Delivery ended — stop sending updates
                    trySend(Resource.Empty)
                } else {
                    trySend(Resource.Success(location))
                }
            }
        }

        awaitClose { registration.remove() }
    }

    /**
     * Marks the `deliveryLocations/{orderId}` document as inactive so
     * customers can no longer see the driver's position.
     *
     * Called by [LocationTrackingService] when delivery completes/cancels.
     */
    suspend fun deactivateTracking(orderId: String): Resource<Unit> {
        return try {
            val data = hashMapOf("orderId" to orderId)
            functions.getHttpsCallable("deactivateDriverLocation").call(data).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to deactivate tracking")
        }
    }

    companion object {
        /** Accuracy threshold: GPS fixes worse than this are dropped before writing. */
        const val MAX_ACCEPTABLE_ACCURACY_M = 150f
    }
}
