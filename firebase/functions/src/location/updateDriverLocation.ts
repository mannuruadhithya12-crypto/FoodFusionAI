import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { haversineKm, isValidCoord, speedKmh } from "./geoUtils";

/**
 * updateDriverLocation — callable function
 *
 * Receives a GPS fix from the driver's foreground service and writes it to
 * `deliveryLocations/{orderId}`.
 *
 * Security enforced here (not in Firestore rules) because we need server-side
 * logic (ownership check, rate limiting, spoofing detection):
 *
 *   ✓ Driver must be authenticated
 *   ✓ Driver must own the order (order.deliveryPartner.id === uid)
 *   ✓ Order must be in OUT_FOR_DELIVERY state
 *   ✓ Coordinate bounds validation
 *   ✓ Accuracy gate (>150 m rejected)
 *   ✓ Rate limiting via Firestore timestamp (max 1 write per 5 s)
 *   ✓ Impossible-speed detection → flags document & creates alert
 *
 * Part P, Q, R, AJ, AK, AL, AT
 */
export const updateDriverLocation = functions.https.onCall(async (data, context) => {
  // ── Auth ────────────────────────────────────────────────────────────────────
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
  }
  const uid = context.auth.uid;

  // ── Input validation ────────────────────────────────────────────────────────
  const { orderId, latitude, longitude, accuracy, heading, speed, timestamp } = data;

  if (!orderId || typeof orderId !== "string") {
    throw new functions.https.HttpsError("invalid-argument", "orderId is required.");
  }
  if (!isValidCoord(latitude, longitude)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      `Invalid coordinates: lat=${latitude} lon=${longitude}. Must be within [-90,90] and [-180,180].`
    );
  }
  const accuracyM = typeof accuracy === "number" ? accuracy : 999;
  if (accuracyM > 150) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      `GPS accuracy too low: ${accuracyM.toFixed(0)} m. Minimum 150 m required.`
    );
  }

  const db = admin.firestore();

  // ── Order ownership & state check ───────────────────────────────────────────
  const orderRef = db.collection("orders").doc(orderId);
  const orderSnap = await orderRef.get();
  if (!orderSnap.exists) {
    throw new functions.https.HttpsError("not-found", "Order not found.");
  }
  const orderData = orderSnap.data()!;

  if (orderData.deliveryPartner?.id !== uid) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "You are not the assigned driver for this order."
    );
  }
  if (orderData.orderStatus !== "OUT_FOR_DELIVERY") {
    throw new functions.https.HttpsError(
      "failed-precondition",
      `Cannot update location — order status is ${orderData.orderStatus}.`
    );
  }

  const locationRef = db.collection("deliveryLocations").doc(orderId);

  // ── Rate limiting (max 1 write per 5 seconds) ───────────────────────────────
  const existingSnap = await locationRef.get();
  if (existingSnap.exists) {
    const prevUpdatedAt = existingSnap.data()?.updatedAt?.toMillis?.() ?? 0;
    const ageMs = Date.now() - prevUpdatedAt;
    if (ageMs < 5_000) {
      // Silently drop — not an error, just rate-limited
      return { success: true, rateLimited: true };
    }
  }

  // ── Spoofing detection (impossible speed) ────────────────────────────────────
  let suspiciousMovementFlag = false;
  const prevData = existingSnap.exists ? existingSnap.data() : null;
  if (prevData && prevData.latitude && prevData.longitude && prevData.timestamp) {
    const kmh = speedKmh(
      { latitude: prevData.latitude, longitude: prevData.longitude, timestamp: prevData.timestamp },
      { latitude, longitude, timestamp: timestamp ?? Date.now() }
    );
    // Flag if speed exceeds 120 km/h (impossible for a delivery bike in urban India)
    if (kmh > 120) {
      suspiciousMovementFlag = true;
      console.warn(
        `SUSPICIOUS_LOCATION_MOVEMENT: order=${orderId} driver=${uid} ` +
        `speed=${kmh.toFixed(1)} km/h from (${prevData.latitude},${prevData.longitude}) ` +
        `to (${latitude},${longitude})`
      );
      // Write alert to locationAlerts collection for admin visibility (Part AK)
      await db.collection("locationAlerts").add({
        orderId,
        driverId: uid,
        prevLat: prevData.latitude,
        prevLon: prevData.longitude,
        newLat: latitude,
        newLon: longitude,
        speedKmh: kmh,
        detectedAt: admin.firestore.FieldValue.serverTimestamp(),
        type: "IMPOSSIBLE_SPEED",
        resolved: false,
      });
    }
  }

  // ── Write GPS fix ────────────────────────────────────────────────────────────
  await locationRef.set({
    orderId,
    driverId:               uid,
    latitude,
    longitude,
    accuracy:               accuracyM,
    heading:                typeof heading === "number" ? heading : 0,
    speed:                  typeof speed   === "number" ? speed   : 0,
    timestamp:              timestamp ?? Date.now(),
    updatedAt:              admin.firestore.FieldValue.serverTimestamp(),
    isActive:               true,
    suspiciousMovementFlag,
  });

  return { success: true, rateLimited: false, suspiciousMovementFlag };
});
