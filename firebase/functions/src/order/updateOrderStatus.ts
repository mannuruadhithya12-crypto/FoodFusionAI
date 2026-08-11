import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { OrderStatus, canTransition } from "./orderStateMachine";
import { haversineKm } from "../location/geoUtils";

export const updateOrderStatus = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "User must be logged in to update order status."
        );
    }

    // NOTE: In production add role verification here (admin/restaurant custom claims).
    const uid = context.auth.uid;

    const orderId  = data.orderId;
    const newStatus = data.newStatus as OrderStatus;
    const message   = data.message || "";

    if (!orderId || !newStatus) {
        throw new functions.https.HttpsError("invalid-argument", "Order ID and newStatus are required.");
    }

    const db       = admin.firestore();
    const orderRef = db.collection("orders").doc(orderId);

    try {
        await db.runTransaction(async (transaction) => {
            const orderDoc = await transaction.get(orderRef);

            if (!orderDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Order not found.");
            }

            const orderData    = orderDoc.data();
            const currentStatus = orderData?.orderStatus as OrderStatus;

            // 2. State machine validation
            if (!canTransition(currentStatus, newStatus)) {
                throw new functions.https.HttpsError(
                    "failed-precondition",
                    `Invalid transition from ${currentStatus} to ${newStatus}`
                );
            }

            // Build status history entry
            const statusHistory = orderData?.statusHistory || [];
            statusHistory.push({
                status:         newStatus,
                previousStatus: currentStatus,
                timestamp:      Date.now(),
                updatedBy:      uid,
                message,
            });

            const updateData: Record<string, any> = {
                orderStatus:  newStatus,
                updatedAt:    admin.firestore.FieldValue.serverTimestamp(),
                statusHistory,
            };

            // Phase 16: dynamic ETA when transitioning to PREPARING
            // Replaces the old hardcoded +30 min with a distance-aware calculation.
            if (newStatus === OrderStatus.PREPARING) {
                updateData.estimatedDeliveryAt = computeEta(orderData);
            }

            transaction.update(orderRef, updateData);
        });

        return { success: true, message: `Order status updated to ${newStatus}` };
    } catch (error) {
        console.error("Error updating order status:", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", "An error occurred while updating the order status.");
    }
});

/**
 * Dynamic ETA: 15 min prep + travel time at 25 km/h + 5 min handoff buffer.
 * Falls back to 30 min when coordinates are unavailable.
 *
 * Phase 16 replacement for the hardcoded "+30 minutes" logic.
 */
function computeEta(orderData: FirebaseFirestore.DocumentData | undefined): number {
    try {
        const addressSnap = orderData?.addressSnapshot;
        const restLat     = orderData?.restaurantLat  ?? 0;
        const restLon     = orderData?.restaurantLon  ?? 0;
        const custLat     = addressSnap?.latitude     ?? 0;
        const custLon     = addressSnap?.longitude    ?? 0;

        if (restLat !== 0 && custLat !== 0) {
            const distKm      = haversineKm(
                { latitude: restLat, longitude: restLon },
                { latitude: custLat, longitude: custLon }
            );
            const travelMins  = Math.ceil((distKm / 25) * 60); // 25 km/h urban speed
            const prepMins    = 15;
            const bufferMins  = 5;
            const totalMins   = prepMins + travelMins + bufferMins;
            return Date.now() + totalMins * 60_000;
        }
    } catch (e) {
        console.warn("computeEta: could not compute distance ETA, using fallback", e);
    }
    // Fallback: 30 min when restaurant/customer coordinates are not yet stored
    return Date.now() + 30 * 60_000;
}
