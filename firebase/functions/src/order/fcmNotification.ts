import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const onOrderStatusUpdated = functions.firestore
    .document("orders/{orderId}")
    .onUpdate(async (change, context) => {
        const orderId = context.params.orderId;
        const beforeData = change.before.data();
        const afterData = change.after.data();

        const beforeStatus = beforeData.orderStatus;
        const afterStatus = afterData.orderStatus;
        const userId = afterData.userId;

        // Ensure status actually changed
        if (beforeStatus === afterStatus) {
            return null;
        }

        const db = admin.firestore();

        // Ensure we don't send duplicate notifications. We'll use a notifications subcollection
        // on the user doc to maintain idempotency based on orderId + newStatus
        const notifId = `${orderId}_${afterStatus}`;
        const notifRef = db.collection("users").doc(userId).collection("notifications").doc(notifId);

        try {
            await db.runTransaction(async (transaction) => {
                const notifDoc = await transaction.get(notifRef);
                if (notifDoc.exists) {
                    // Notification already processed
                    console.log(`Notification ${notifId} already sent`);
                    return;
                }

                // Get user's FCM token
                const userDoc = await transaction.get(db.collection("users").doc(userId));
                const fcmToken = userDoc.data()?.fcmToken;

                if (fcmToken) {
                    let title = "";
                    let body = "";

                    switch (afterStatus) {
                        case "CONFIRMED":
                            title = "Order Confirmed";
                            body = "Your order has been confirmed.";
                            break;
                        case "PREPARING":
                            title = "Preparing";
                            body = "The restaurant has started preparing your food.";
                            break;
                        case "READY_FOR_PICKUP":
                            title = "Ready";
                            body = "Your order is ready for pickup.";
                            break;
                        case "OUT_FOR_DELIVERY":
                            title = "Out for Delivery";
                            body = "Your order is on the way.";
                            break;
                        case "DELIVERED":
                            title = "Delivered";
                            body = "Your order has been delivered.";
                            break;
                        case "CANCELLED":
                            title = "Order Cancelled";
                            body = "Your order has been cancelled.";
                            break;
                        default:
                            return; // No notification for other statuses
                    }

                    const message = {
                        notification: {
                            title: title,
                            body: body
                        },
                        data: {
                            orderId: orderId,
                            type: "ORDER_STATUS_UPDATE"
                        },
                        token: fcmToken
                    };

                    await admin.messaging().send(message);
                    console.log(`Successfully sent FCM for order ${orderId} status ${afterStatus}`);
                }

                // Mark as processed
                transaction.set(notifRef, {
                    sentAt: admin.firestore.FieldValue.serverTimestamp(),
                    orderId: orderId,
                    status: afterStatus
                });
            });

        } catch (error) {
            console.error("Error sending order status FCM:", error);
        }

        return null;
    });
