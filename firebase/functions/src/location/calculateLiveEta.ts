import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { haversineKm, isValidCoord } from "./geoUtils";

/**
 * calculateLiveEta — callable function
 *
 * Computes a server-authoritative ETA for an active delivery.
 *
 * Inputs:
 *   orderId — active order
 *
 * Algorithm:
 *   1. Read driver's current location from deliveryLocations/{orderId}
 *   2. Read customer's delivery coordinates from order.addressSnapshot
 *   3. Compute Haversine distance driver → customer
 *   4. Add remaining prep time based on order status
 *   5. Apply ±20 % window
 *   6. Mark APPROXIMATE (straight-line) vs AVAILABLE (road estimate not possible server-side)
 *
 * Note: Real road routing (Directions API) requires an HTTP call from Node.js
 * which would need the Maps API key stored in Firebase Secret Manager.
 * The Android client's RoutingService handles road-based ETA since it already
 * has the Maps SDK wired in.  This function provides the fallback/authoritative
 * straight-line estimate.
 *
 * Parts W, X, Y — ETA architecture
 */
export const calculateLiveEta = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
  }

  const { orderId } = data;
  if (!orderId || typeof orderId !== "string") {
    throw new functions.https.HttpsError("invalid-argument", "orderId is required.");
  }

  const db = admin.firestore();

  // ── Load order ────────────────────────────────────────────────────────────
  const orderSnap = await db.collection("orders").doc(orderId).get();
  if (!orderSnap.exists) {
    throw new functions.https.HttpsError("not-found", "Order not found.");
  }
  const order = orderSnap.data()!;

  // Only the order owner or driver may query ETA
  const uid = context.auth.uid;
  const isOwner  = order.userId === uid;
  const isDriver = order.deliveryPartner?.id === uid;
  if (!isOwner && !isDriver) {
    throw new functions.https.HttpsError("permission-denied", "Access denied.");
  }

  // ── Load driver location ──────────────────────────────────────────────────
  const locationSnap = await db.collection("deliveryLocations").doc(orderId).get();
  const location = locationSnap.exists ? locationSnap.data() : null;

  const destLat = order.addressSnapshot?.latitude  ?? 0;
  const destLon = order.addressSnapshot?.longitude ?? 0;

  // If no coordinates available, fall back to stored estimatedDeliveryAt
  if (!location || !isValidCoord(location.latitude, location.longitude) ||
      !isValidCoord(destLat, destLon) || (destLat === 0 && destLon === 0)) {
    const eta = order.estimatedDeliveryAt;
    if (eta && eta > Date.now()) {
      const remainingMs = eta - Date.now();
      const mins = Math.ceil(remainingMs / 60_000);
      const buf  = Math.max(1, Math.round(mins * 0.2));
      return {
        state:      "APPROXIMATE",
        minMinutes: Math.max(1, mins - buf),
        maxMinutes: mins + buf,
        source:     "stored_estimate",
      };
    }
    return { state: "UNAVAILABLE", minMinutes: 0, maxMinutes: 0, source: "no_data" };
  }

  // ── Compute Haversine ETA ─────────────────────────────────────────────────
  const distKm = haversineKm(
    { latitude: location.latitude, longitude: location.longitude },
    { latitude: destLat, longitude: destLon }
  );

  // Prep remaining based on status
  const prepRemaining: Record<string, number> = {
    CONFIRMED:          20,
    PREPARING:          10,
    READY_FOR_PICKUP:    2,
    OUT_FOR_DELIVERY:    0,
  };
  const prepMins = prepRemaining[order.orderStatus] ?? 0;

  // Urban delivery speed: 25 km/h
  const travelMins = Math.ceil((distKm / 25) * 60);
  const total = travelMins + prepMins;
  const buf   = Math.max(1, Math.round(total * 0.2));

  // Freshness check
  const updatedAt   = location.updatedAt?.toMillis?.() ?? 0;
  const ageMs       = Date.now() - updatedAt;
  const state       = ageMs > 5 * 60_000 ? "STALE" : "APPROXIMATE";

  return {
    state,
    minMinutes: Math.max(1, total - buf),
    maxMinutes: total + buf,
    distanceKm: parseFloat(distKm.toFixed(2)),
    source:     "haversine",
  };
});
