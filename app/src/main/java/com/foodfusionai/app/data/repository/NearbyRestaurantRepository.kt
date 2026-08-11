package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.location.DistanceCalculator
import com.foodfusionai.app.data.location.GeoHashUtil
import com.foodfusionai.app.data.location.GeoPoint
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.utils.Resource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Fetches restaurants near a customer's location using Firestore geohash queries.
 *
 * ── Why geohash? ──────────────────────────────────────────────────────────────
 * Firestore doesn't support native radius queries.  The standard approach is:
 *  1. Store a geohash string on every restaurant document.
 *  2. Compute the set of geohash prefixes (at precision 5 ≈ 4.9 km × 4.9 km)
 *     that cover the bounding box around the customer.
 *  3. Run one bounded range query per prefix:
 *         geohash >= prefix  AND  geohash <= prefix + "\uf8ff"
 *  4. Merge results and post-filter by exact Haversine distance.
 *
 * This is equivalent to what the official geofire-common library does,
 * without a runtime dependency.
 *
 * ── Cost control ──────────────────────────────────────────────────────────────
 * At precision 5 the bounding box contains at most ~9 prefix cells, so we
 * issue at most 9 Firestore queries per search.  Results are deduplicated
 * by restaurant ID before returning.
 *
 * Restaurants without geohash (legacy documents) are not returned by prefix
 * queries.  The admin should backfill geohash values via the Cloud Function
 * `backfillRestaurantGeohashes` (to be added in Phase 16 Cloud Functions).
 */
class NearbyRestaurantRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Returns restaurants within [radiusKm] of [customerLocation], sorted by distance.
     *
     * @param customerLocation  Customer GPS fix
     * @param radiusKm          Search radius (default 10 km)
     * @param onlyOpen          When true, filter to restaurants with `isOpen == true`
     */
    suspend fun getNearbyRestaurants(
        customerLocation: GeoPoint,
        radiusKm: Double = 10.0,
        onlyOpen: Boolean = true
    ): Resource<List<NearbyRestaurant>> = withContext(Dispatchers.IO) {
        try {
            val prefixes = GeoHashUtil.queryPrefixes(customerLocation, radiusKm, precision = 5)

            // Run all prefix queries concurrently
            val results = prefixes.map { prefix ->
                async {
                    val (start, end) = GeoHashUtil.rangeForPrefix(prefix)
                    try {
                        firestore.collection("restaurants")
                            .whereGreaterThanOrEqualTo("geohash", start)
                            .whereLessThanOrEqualTo("geohash", end)
                            .get()
                            .await()
                            .toObjects(Restaurant::class.java)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()

            // Deduplicate, filter, and sort
            val seen = HashSet<String>()
            val nearby = results.flatten()
                .filter { restaurant ->
                    // Dedup
                    if (!seen.add(restaurant.id)) return@filter false
                    // Open filter
                    if (onlyOpen && !restaurant.isOpen) return@filter false
                    // Must have coordinates
                    if (!restaurant.hasCoordinates) return@filter false
                    // Exact distance post-filter
                    val restaurantPoint = GeoPoint(restaurant.latitude, restaurant.longitude)
                    val distKm = DistanceCalculator.distanceKm(customerLocation, restaurantPoint)
                    distKm <= radiusKm
                }
                .map { restaurant ->
                    val restaurantPoint = GeoPoint(restaurant.latitude, restaurant.longitude)
                    val distKm = DistanceCalculator.distanceKm(customerLocation, restaurantPoint)
                    NearbyRestaurant(
                        restaurant = restaurant,
                        distanceKm = distKm,
                        distanceLabel = DistanceCalculator.formatDistance(customerLocation, restaurantPoint),
                        isDeliverable = distKm <= restaurant.effectiveDeliveryRadiusKm
                    )
                }
                .sortedBy { it.distanceKm }

            Resource.Success(nearby)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch nearby restaurants")
        }
    }
}

/**
 * A restaurant augmented with distance information relative to the customer.
 */
data class NearbyRestaurant(
    val restaurant: Restaurant,
    /** Straight-line distance from customer to restaurant in km. */
    val distanceKm: Double,
    /** Human-readable distance, e.g. "2.4 km". */
    val distanceLabel: String,
    /** True when the customer's location is within [Restaurant.effectiveDeliveryRadiusKm]. */
    val isDeliverable: Boolean
)
