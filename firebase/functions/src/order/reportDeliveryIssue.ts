import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const reportDeliveryIssue = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Auth required");
    }

    const { orderId, reason, description } = data;
    if (!orderId || !reason) {
        throw new functions.https.HttpsError("invalid-argument", "Order ID and reason are required");
    }

    const uid = context.auth.uid;
    const db = admin.firestore();
    const orderRef = db.collection("orders").doc(orderId);

    try {
        await db.runTransaction(async (transaction) => {
            const orderDoc = await transaction.get(orderRef);
            if (!orderDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Order not found");
            }

            const order = orderDoc.data()!;
            if (!order.deliveryPartner || order.deliveryPartner.id !== uid) {
                throw new functions.https.HttpsError("permission-denied", "You are not the assigned driver for this order");
            }

            // Flag order with issue report details
            const issueDetails = {
                reason,
                description: description || "",
                reportedBy: uid,
                reportedAt: Date.now(),
                status: "PENDING_RESOLUTION"
            };

            transaction.update(orderRef, {
                deliveryIssue: issueDetails,
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });

            // Write audit log
            const logRef = db.collection("auditLogs").doc();
            transaction.set(logRef, {
                logId: logRef.id,
                actorUid: uid,
                actorRole: "DELIVERY_PARTNER",
                action: "DELIVERY_FAILED",
                orderId,
                driverId: uid,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                details: issueDetails
            });
        });

        return { success: true, message: "Delivery issue reported successfully to operations" };
    } catch (error: any) {
        console.error("Error in reportDeliveryIssue:", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal error");
    }
});
