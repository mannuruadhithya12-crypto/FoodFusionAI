import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { issueOrderRewards } from "../utils/rewardSystem";

export const verifyDeliveryOtp = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Auth required");
    }

    const { orderId, otp } = data;
    if (!orderId || !otp) {
        throw new functions.https.HttpsError("invalid-argument", "Order ID and OTP are required");
    }

    const uid = context.auth.uid;
    const db = admin.firestore();
    const orderRef = db.collection("orders").doc(orderId);

    try {
        return await db.runTransaction(async (transaction) => {
            const orderDoc = await transaction.get(orderRef);
            if (!orderDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Order not found");
            }

            const order = orderDoc.data()!;
            if (!order.deliveryPartner || order.deliveryPartner.id !== uid) {
                throw new functions.https.HttpsError("permission-denied", "You are not the assigned driver for this order");
            }

            if (order.orderStatus !== "OUT_FOR_DELIVERY") {
                throw new functions.https.HttpsError("failed-precondition", "Order is not out for delivery");
            }

            // OTP Verification check
            if (order.deliveryOtp !== otp) {
                throw new functions.https.HttpsError("invalid-argument", "Incorrect OTP. Please check with customer and try again.");
            }

            // Prepare status history
            const statusHistory = order.statusHistory || [];
            statusHistory.push({
                status: "DELIVERED",
                previousStatus: "OUT_FOR_DELIVERY",
                timestamp: Date.now(),
                updatedBy: uid,
                message: "Delivery verified via OTP"
            });

            // Update order
            transaction.update(orderRef, {
                orderStatus: "DELIVERED",
                deliveredAt: Date.now(),
                statusHistory,
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });

            // Update driver status back to ONLINE
            const driverRef = db.collection("drivers").doc(uid);
            transaction.update(driverRef, {
                availability: "ONLINE"
            });

            // Record Earnings
            const deliveryFee = order.deliveryFee || 50.0; // Fallback default delivery fee
            const earningsEntryRef = db.collection("driverEarnings").doc(uid).collection("entries").doc();
            const earningsEntry = {
                entryId: earningsEntryRef.id,
                orderId,
                amount: deliveryFee,
                timestamp: Date.now(),
                type: "DELIVERY_FEE",
                status: "PAID", // Fulfill directly or mark pending settlement
                createdAt: admin.firestore.FieldValue.serverTimestamp()
            };
            transaction.set(earningsEntryRef, earningsEntry);

            const earningsSummaryRef = db.collection("driverEarnings").doc(uid);
            const earningsSummaryDoc = await transaction.get(earningsSummaryRef);

            if (earningsSummaryDoc.exists) {
                transaction.update(earningsSummaryRef, {
                    totalEarnings: admin.firestore.FieldValue.increment(deliveryFee),
                    todayEarnings: admin.firestore.FieldValue.increment(deliveryFee),
                    updatedAt: admin.firestore.FieldValue.serverTimestamp()
                });
            } else {
                transaction.set(earningsSummaryRef, {
                    driverId: uid,
                    totalEarnings: deliveryFee,
                    todayEarnings: deliveryFee,
                    weeklyEarnings: deliveryFee,
                    monthlyEarnings: deliveryFee,
                    createdAt: admin.firestore.FieldValue.serverTimestamp(),
                    updatedAt: admin.firestore.FieldValue.serverTimestamp()
                });
            }

            // Write immutable audit log
            const logRef = db.collection("auditLogs").doc();
            transaction.set(logRef, {
                logId: logRef.id,
                actorUid: uid,
                actorRole: "DELIVERY_PARTNER",
                action: "DELIVERY_COMPLETED",
                orderId,
                driverId: uid,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                before: { orderStatus: "OUT_FOR_DELIVERY" },
                after: { orderStatus: "DELIVERED" }
            });

            // Clean up live location streaming data to preserve driver privacy
            const locationRef = db.collection("deliveryLocations").doc(orderId);
            transaction.delete(locationRef);

            // Issue customer rewards
            const totalAmount = order.totalAmount || 0;
            await issueOrderRewards(db, transaction, order.userId, orderId, totalAmount);

            return { success: true, message: "Delivery confirmed and earnings registered." };
        });
    } catch (error: any) {
        console.error("Error in verifyDeliveryOtp:", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal error");
    }
});
