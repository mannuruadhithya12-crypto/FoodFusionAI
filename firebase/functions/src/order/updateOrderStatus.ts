import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { OrderStatus, canTransition } from "./orderStateMachine";

export const updateOrderStatus = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "User must be logged in to update order status."
        );
    }
    
    // In a real application, you would verify if the user has 'ADMIN' or 'RESTAURANT' role here.
    // For this implementation, we will allow it but in production you MUST verify roles.
    const uid = context.auth.uid;
    
    const orderId = data.orderId;
    const newStatus = data.newStatus as OrderStatus;
    const message = data.message || "";

    if (!orderId || !newStatus) {
        throw new functions.https.HttpsError("invalid-argument", "Order ID and newStatus are required.");
    }

    const db = admin.firestore();
    const orderRef = db.collection("orders").doc(orderId);

    try {
        await db.runTransaction(async (transaction) => {
            const orderDoc = await transaction.get(orderRef);

            if (!orderDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Order not found.");
            }

            const orderData = orderDoc.data();
            const currentStatus = orderData?.orderStatus as OrderStatus;

            // 2. State Validation
            if (!canTransition(currentStatus, newStatus)) {
                throw new functions.https.HttpsError(
                    "failed-precondition",
                    `Invalid transition from ${currentStatus} to ${newStatus}`
                );
            }

            // Prepare status history
            const statusHistory = orderData?.statusHistory || [];
            const newHistoryEvent = {
                status: newStatus,
                previousStatus: currentStatus,
                timestamp: Date.now(),
                updatedBy: uid,
                message: message
            };

            statusHistory.push(newHistoryEvent);

            const updateData: any = {
                orderStatus: newStatus,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                statusHistory: statusHistory
            };

            // Calculate ETA logic if transitioning to PREPARING
            if (newStatus === OrderStatus.PREPARING) {
                updateData.estimatedDeliveryAt = Date.now() + (30 * 60 * 1000); // +30 minutes
            }

            // 3. Perform Update
            transaction.update(orderRef, updateData);
        });

        return { success: true, message: `Order status updated to ${newStatus}` };
    } catch (error) {
        console.error("Error updating order status:", error);
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }
        throw new functions.https.HttpsError("internal", "An error occurred while updating the order status.");
    }
});
