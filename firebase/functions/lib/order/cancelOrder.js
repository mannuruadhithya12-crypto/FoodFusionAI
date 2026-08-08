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
exports.cancelOrder = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
const orderStateMachine_1 = require("./orderStateMachine");
exports.cancelOrder = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be logged in to cancel an order.");
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
            if ((orderData === null || orderData === void 0 ? void 0 : orderData.userId) !== uid) {
                throw new functions.https.HttpsError("permission-denied", "You can only cancel your own orders.");
            }
            const currentStatus = orderData === null || orderData === void 0 ? void 0 : orderData.orderStatus;
            // 3. State Validation
            if (!(0, orderStateMachine_1.canCancel)(currentStatus)) {
                throw new functions.https.HttpsError("failed-precondition", `Order cannot be cancelled from current status: ${currentStatus}`);
            }
            // Prepare status history
            const statusHistory = (orderData === null || orderData === void 0 ? void 0 : orderData.statusHistory) || [];
            const newHistoryEvent = {
                status: orderStateMachine_1.OrderStatus.CANCELLED,
                previousStatus: currentStatus,
                timestamp: Date.now(),
                updatedBy: uid,
                message: cancelReason
            };
            statusHistory.push(newHistoryEvent);
            // 4. Perform Update
            transaction.update(orderRef, {
                orderStatus: orderStateMachine_1.OrderStatus.CANCELLED,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                statusHistory: statusHistory
            });
        });
        return { success: true, message: "Order successfully cancelled." };
    }
    catch (error) {
        console.error("Error cancelling order:", error);
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }
        throw new functions.https.HttpsError("internal", "An error occurred while cancelling the order.");
    }
});
//# sourceMappingURL=cancelOrder.js.map