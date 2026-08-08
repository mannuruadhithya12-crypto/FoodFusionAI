import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { OrderStatus, canCancel } from "./orderStateMachine";

export const cancelOrder = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "User must be logged in to cancel an order."
        );
    }
    
    const uid = context.auth.uid;
    const orderId = data.orderId;
    const cancelReason = data.cancelReason || "Cancelled by user";

    if (!orderId) {
        throw new functions.https.HttpsError("invalid-argument", "Order ID is required.");
    }

    const db = admin.firestore();
    const orderRef = db.collection("orders").doc(orderId);

    try {
        // Run as transaction to prevent race conditions during cancellation
        await db.runTransaction(async (transaction) => {
            const orderDoc = await transaction.get(orderRef);

            if (!orderDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Order not found.");
            }

            const orderData = orderDoc.data();

            // 2. Verify order ownership
            if (orderData?.userId !== uid) {
                throw new functions.https.HttpsError("permission-denied", "You can only cancel your own orders.");
            }

            const currentStatus = orderData?.orderStatus as OrderStatus;

            // 3. State Validation
            if (!canCancel(currentStatus)) {
                throw new functions.https.HttpsError(
                    "failed-precondition",
                    `Order cannot be cancelled from current status: ${currentStatus}`
                );
            }

            // Prepare status history
            const statusHistory = orderData?.statusHistory || [];
            const newHistoryEvent = {
                status: OrderStatus.CANCELLED,
                previousStatus: currentStatus,
                timestamp: Date.now(),
                updatedBy: uid,
                message: cancelReason
            };

            statusHistory.push(newHistoryEvent);

            // 4. Perform Update
            transaction.update(orderRef, {
                orderStatus: OrderStatus.CANCELLED,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                statusHistory: statusHistory
            });
        });

        return { success: true, message: "Order successfully cancelled." };
    } catch (error) {
        console.error("Error cancelling order:", error);
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }
        throw new functions.https.HttpsError("internal", "An error occurred while cancelling the order.");
    }
});
