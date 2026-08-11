import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { OrderStatus, canTransition } from "./orderStateMachine";
import { issueOrderRewards } from "../utils/rewardSystem";

export const updateOrderStatus = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "User must be logged in to update order status."
        );
    }
    
    // Role Verification
    const role = context.auth.token.role;
    if (role !== 'ADMIN' && role !== 'RESTAURANT_OWNER' && role !== 'RESTAURANT_MANAGER' && role !== 'RESTAURANT_STAFF') {
        throw new functions.https.HttpsError("permission-denied", "Unauthorized. Only Admins or Partners can update order status.");
    }
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

            if (role !== 'ADMIN') {
                const userDoc = await transaction.get(db.collection("users").doc(uid));
                const restaurantIds = userDoc.data()?.restaurantIds || [];
                if (!restaurantIds.includes(orderData?.restaurantId)) {
                    throw new functions.https.HttpsError("permission-denied", "You can only update orders for your own restaurants.");
                }
            }

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

            if (newStatus === OrderStatus.DELIVERED) {
                const totalAmount = orderData?.totalAmount || 0;
                await issueOrderRewards(db, transaction, orderData?.userId, orderId, totalAmount);
            }
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
