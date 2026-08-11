import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { haversineKm, isValidCoord } from "./geoUtils";

/**
 * validateDeliveryLocation — callable function
 *
 * Server-authoritative check: is the customer's address within the
 * restaurant's delivery range?  Also computes the delivery fee.
 *
 * Returns:
 *   isDeliverable  — boolean
 *   distanceKm     — Haversine distance, server-computed
 *   deliveryFee    — INR, server-authoritative (client must NOT trust its own fee)
 *   reason         — human-readable error when !isDeliverable
 *   zoneId         — matching delivery zone ID if found
 *
 * Parts M, N, O — delivery radius, zone integration, server-authoritative fee
 */
export const validateDeliveryLocation = functions.https.onCall(async (data, context) => {
  // ── Auth ────────────────────────────────────────────────────────────────────
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
  }

  // ── Input validation ────────────────────────────────────────────────────────
  const { restaurantId, customerLat, customerLon } = data;
  if (!restaurantId || typeof restaurantId !== "string") {
    throw new functions.https.HttpsError("invalid-argument", "restaurantId is required.");
  }
  if (!isValidCoord(customerLat, customerLon)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      `Invalid customer coordinates: lat=${customerLat} lon=${customerLon}`
    );
  }

  const db = admin.firestore();

  // ── Load restaurant ──────────────────────────────────────────────────────────
  const restaurantSnap = await db.collection("restaurants").doc(restaurantId).get();
  if (!restaurantSnap.exists) {
    throw new functions.https.HttpsError("not-found", "Restaurant not found.");
  }
  const restaurant = restaurantSnap.data()!;

  const restLat = restaurant.latitude  ?? 0;
  const restLon = restaurant.longitude ?? 0;

  if (!isValidCoord(restLat, restLon) || (restLat === 0 && restLon === 0)) {
    // Restaurant has no coordinates — allow but warn
    return {
      isDeliverable: true,
      distanceKm:    0,
      deliveryFee:   restaurant.deliveryFee ?? 40,
      reason:        "Restaurant location not configured — using default fee",
      zoneId:        "",
    };
  }

  // ── Compute distance ─────────────────────────────────────────────────────────
  const distanceKm = haversineKm(
    { latitude: restLat,     longitude: restLon    },
    { latitude: customerLat, longitude: customerLon }
  );

  const deliveryRadiusKm = restaurant.deliveryRadiusKm > 0
    ? restaurant.deliveryRadiusKm
    : 8.0; // default 8 km

  // ── Check delivery zone ───────────────────────────────────────────────────────
  let zoneId = "";
  let zoneFee: number | null = null;
  let zoneMinOrder: number | null = null;
  let zoneMaxDistance: number | null = null;

  if (restaurant.deliveryZoneId) {
    const zoneSnap = await db.collection("deliveryZones").doc(restaurant.deliveryZoneId).get();
    if (zoneSnap.exists) {
      const zone = zoneSnap.data()!;
      if (zone.isActive) {
        zoneId = zoneSnap.id;
        zoneFee         = zone.deliveryFee          ?? null;
        zoneMinOrder    = zone.minimumOrderAmount    ?? null;
        zoneMaxDistance = zone.maximumDeliveryDistanceKm ?? null;
      }
    }
  }

  // Apply zone max distance override if set
  const effectiveMaxKm = zoneMaxDistance ?? deliveryRadiusKm;

  if (distanceKm > effectiveMaxKm) {
    return {
      isDeliverable: false,
      distanceKm,
      deliveryFee:   0,
      reason: `Your address is ${distanceKm.toFixed(1)} km away. Delivery is available up to ${effectiveMaxKm.toFixed(1)} km.`,
      zoneId,
    };
  }

  // ── Compute delivery fee ────────────────────────────────────────────────────
  const deliveryFee = computeDeliveryFee(distanceKm, restaurant, zoneFee);

  return {
    isDeliverable: true,
    distanceKm,
    deliveryFee,
    reason: "",
    zoneId,
  };
});

/**
 * Delivery fee tiers (server-authoritative):
 *   Zone fee overrides restaurant fee if present.
 *   Otherwise: ₹0 for ≤3 km, ₹25 for 3–6 km, ₹49 for 6–10 km, ₹79 for >10 km
 */
function computeDeliveryFee(
  distanceKm: number,
  restaurant: FirebaseFirestore.DocumentData,
  zoneFee: number | null
): number {
  if (zoneFee !== null) return zoneFee;
  if (restaurant.deliveryFee !== undefined && restaurant.deliveryFee >= 0) {
    return restaurant.deliveryFee;
  }
  // Distance-based tiered fallback
  if (distanceKm <= 3)  return 0;
  if (distanceKm <= 6)  return 25;
  if (distanceKm <= 10) return 49;
  return 79;
}
