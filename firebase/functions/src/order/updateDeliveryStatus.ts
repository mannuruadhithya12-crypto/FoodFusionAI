import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { OrderStatus } from "./orderStateMachine";

export const updateDeliveryStatus = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Auth required");
    }

    const { orderId, newStatus, message } = data;
    if (!orderId || !newStatus) {
        throw new functions.https.HttpsError("invalid-argument", "Order ID and newStatus are required");
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
            const currentStatus = order.orderStatus as OrderStatus;

            // 1. Verify driver ownership of this delivery
            if (!order.deliveryPartner || order.deliveryPartner.id !== uid) {
                throw new functions.https.HttpsError("permission-denied", "You are not the assigned driver for this order");
            }

            // 2. Verify driver profile status
            const driverDoc = await transaction.get(db.collection("drivers").doc(uid));
            if (!driverDoc.exists || driverDoc.data()?.status !== "APPROVED") {
                throw new functions.https.HttpsError("permission-denied", "Driver is not approved or active");
            }

            // 3. Validate state transition
            if (newStatus === "OUT_FOR_DELIVERY") {
                if (currentStatus !== "READY_FOR_PICKUP") {
                    throw new functions.https.HttpsError("failed-precondition", "Order must be READY_FOR_PICKUP to start delivery");
                }
            } else if (newStatus === "DELIVERED") {
                // If they want to complete delivery without OTP, we check if OTP verification is bypassable or if we need verifyDeliveryOtp
                // For secure E2E OTP validation, we require calling verifyDeliveryOtp instead.
                throw new functions.https.HttpsError("failed-precondition", "Use verifyDeliveryOtp function to complete delivery with secure OTP verification");
            } else {
                throw new functions.https.HttpsError("failed-precondition", `Drivers are not allowed to transition order to ${newStatus}`);
            }

            const statusHistory = order.statusHistory || [];
            statusHistory.push({
                status: newStatus,
                previousStatus: currentStatus,
                timestamp: Date.now(),
                updatedBy: uid,
                message: message || "Status updated by driver"
            });

            const updateData: any = {
                orderStatus: newStatus,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                statusHistory: statusHistory
            };

            if (newStatus === "OUT_FOR_DELIVERY") {
                updateData.pickedUpAt = Date.now();
                // When starting delivery, generate a 4-digit numeric OTP for the customer to confirm delivery
                const otp = Math.floor(1000 + Math.random() * 9000).toString();
                updateData.deliveryOtp = otp;

                // Send OTP to customer via FCM notification
                if (order.userId) {
                    const customerDoc = await db.collection("users").doc(order.userId).get();
                    const customerToken = customerDoc.data()?.fcmToken;
                    if (customerToken) {
                        const otpMsg = {
                            notification: {
                                title: "Order Out for Delivery",
                                body: `Your order is on the way! Provide OTP ${otp} to the driver to receive your delivery.`
                            },
                            data: {
                                orderId,
                                type: "DELIVERY_OTP",
                                otp,
                                targetId: orderId
                            },
                            token: customerToken
                        };
                        await admin.messaging().send(otpMsg).catch(console.error);
                    }
                }
            }

            transaction.update(orderRef, updateData);

            // Audit log
            const logRef = db.collection("auditLogs").doc();
            transaction.set(logRef, {
                logId: logRef.id,
                actorUid: uid,
                actorRole: "DELIVERY_PARTNER",
                action: newStatus === "OUT_FOR_DELIVERY" ? "ORDER_PICKED_UP" : "DELIVERY_COMPLETED",
                orderId,
                driverId: uid,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                before: { orderStatus: currentStatus },
                after: { orderStatus: newStatus }
            });

            return { success: true, newStatus, message: `Order status updated to ${newStatus}` };
        });
    } catch (error: any) {
        console.error("Error in updateDeliveryStatus:", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal error");
    }
});
