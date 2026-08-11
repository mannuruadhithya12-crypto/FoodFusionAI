"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.onOrderStatusUpdated = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
exports.onOrderStatusUpdated = functions.firestore
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
            var _a;
            const notifDoc = await transaction.get(notifRef);
            if (notifDoc.exists) {
                // Notification already processed
                console.log(`Notification ${notifId} already sent`);
                return;
            }
            // Get user's FCM token
            const userDoc = await transaction.get(db.collection("users").doc(userId));
            const fcmToken = (_a = userDoc.data()) === null || _a === void 0 ? void 0 : _a.fcmToken;
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
            if (fcmToken) {
                const message = {
                    notification: {
                        title: title,
                        body: body
                    },
                    data: {
                        orderId: orderId,
                        type: "ORDER_UPDATE",
                        targetId: orderId
                    },
                    token: fcmToken
                };
                await admin.messaging().send(message);
                console.log(`Successfully sent FCM for order ${orderId} status ${afterStatus}`);
            }
            // Save full notification document
            transaction.set(notifRef, {
                id: notifId,
                userId: userId,
                title: title,
                body: body,
                type: "ORDER_UPDATE",
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                isRead: false,
                data: {
                    orderId: orderId,
                    status: afterStatus
                }
            });
            // Rewards logic has been moved to verifyDeliveryOtp / status update transactions
        });
    }
    catch (error) {
        console.error("Error sending order status FCM:", error);
    }
    return null;
});
//# sourceMappingURL=fcmNotification.js.map