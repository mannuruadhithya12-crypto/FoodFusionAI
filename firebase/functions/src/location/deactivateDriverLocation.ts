import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

/**
 * deactivateDriverLocation — callable function
 *
 * Marks `deliveryLocations/{orderId}.isActive = false` so customers can no
 * longer see the driver's live GPS position after delivery completes.
 *
 * Called by LocationTrackingService.stopTracking() on the driver's device.
 *
 * Security:
 *   ✓ Caller must be authenticated
 *   ✓ Caller must be the driver assigned to the order
 *
 * Part AD: customer privacy — live GPS hidden after delivery
 * Part AB: location lifecycle — tracking stops with delivery
 */
export const deactivateDriverLocation = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
  }
  const uid = context.auth.uid;
  const { orderId } = data;

  if (!orderId || typeof orderId !== "string") {
    throw new functions.https.HttpsError("invalid-argument", "orderId is required.");
  }

  const db = admin.firestore();

  // Verify the caller is the driver for this order
  const locationSnap = await db.collection("deliveryLocations").doc(orderId).get();
  if (locationSnap.exists && locationSnap.data()?.driverId !== uid) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "You are not the driver for this order."
    );
  }

  await db.collection("deliveryLocations").doc(orderId).set(
    { isActive: false, updatedAt: admin.firestore.FieldValue.serverTimestamp() },
    { merge: true }
  );

  return { success: true };
});
