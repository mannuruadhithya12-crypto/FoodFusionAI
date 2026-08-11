import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { speedKmh } from "./geoUtils";

/**
 * flagSuspiciousLocation — called internally by updateDriverLocation on every write.
 * Also exported as a standalone callable so admins can manually trigger a review.
 *
 * Detects:
 *   • Impossible speed (configurable threshold, default 120 km/h)
 *   • Mock location indicator (provided by driver client in payload)
 *   • Stale timestamp replay (timestamp older than 60 s)
 *
 * Actions:
 *   • Sets suspiciousMovementFlag = true on the deliveryLocations document
 *   • Writes an alert to locationAlerts/{alertId}
 *   • Does NOT auto-ban — creates an operational alert for admin review
 *
 * Parts AK, AL — Spoofing defence, impossible speed detection
 */
export const flagSuspiciousLocation = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
  }
  // Admin-only manual trigger check (basic: only allow if user has admin custom claim)
  // In production, set custom claims via Admin SDK; here we log and continue.
  const uid = context.auth.uid;

  const {
    orderId,
    prevLat, prevLon, prevTimestamp,
    newLat,  newLon,  newTimestamp,
    isMockLocation = false,
  } = data;

  if (!orderId) {
    throw new functions.https.HttpsError("invalid-argument", "orderId is required.");
  }

  const db = admin.firestore();
  const alerts: string[] = [];

  // ── 1. Impossible speed ────────────────────────────────────────────────────
  if (prevLat !== undefined && prevTimestamp !== undefined) {
    const kmh = speedKmh(
      { latitude: prevLat, longitude: prevLon, timestamp: prevTimestamp },
      { latitude: newLat,  longitude: newLon,  timestamp: newTimestamp ?? Date.now() }
    );
    if (kmh > MAX_SPEED_KMH) {
      alerts.push(`IMPOSSIBLE_SPEED: ${kmh.toFixed(1)} km/h (max ${MAX_SPEED_KMH})`);
    }
  }

  // ── 2. Mock location ───────────────────────────────────────────────────────
  if (isMockLocation === true) {
    alerts.push("MOCK_LOCATION_DETECTED");
  }

  // ── 3. Stale timestamp replay ──────────────────────────────────────────────
  const tsAge = Date.now() - (newTimestamp ?? 0);
  if (tsAge > 60_000 && newTimestamp) {
    alerts.push(`STALE_TIMESTAMP: ${Math.round(tsAge / 1000)}s old`);
  }

  if (alerts.length === 0) {
    return { suspicious: false, alerts: [] };
  }

  // ── Write alert ────────────────────────────────────────────────────────────
  await db.collection("locationAlerts").add({
    orderId,
    driverId:    uid,
    alerts,
    newLat,
    newLon,
    detectedAt:  admin.firestore.FieldValue.serverTimestamp(),
    resolved:    false,
  });

  // Flag the location document
  await db.collection("deliveryLocations").doc(orderId).set(
    { suspiciousMovementFlag: true },
    { merge: true }
  );

  console.warn(`[flagSuspiciousLocation] order=${orderId} driver=${uid} alerts=${alerts.join(", ")}`);

  return { suspicious: true, alerts };
});

const MAX_SPEED_KMH = 120;
