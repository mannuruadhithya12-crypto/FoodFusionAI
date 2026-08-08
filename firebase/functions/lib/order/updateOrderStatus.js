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
exports.updateOrderStatus = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
const orderStateMachine_1 = require("./orderStateMachine");
exports.updateOrderStatus = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be logged in to update order status.");
    }
    // In a real application, you would verify if the user has 'ADMIN' or 'RESTAURANT' role here.
    // For this implementation, we will allow it but in production you MUST verify roles.
    const uid = context.auth.uid;
    const orderId = data.orderId;
    const newStatus = data.newStatus;
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
            const currentStatus = orderData === null || orderData === void 0 ? void 0 : orderData.orderStatus;
            // 2. State Validation
            if (!(0, orderStateMachine_1.canTransition)(currentStatus, newStatus)) {
                throw new functions.https.HttpsError("failed-precondition", `Invalid transition from ${currentStatus} to ${newStatus}`);
            }
            // Prepare status history
            const statusHistory = (orderData === null || orderData === void 0 ? void 0 : orderData.statusHistory) || [];
            const newHistoryEvent = {
                status: newStatus,
                previousStatus: currentStatus,
                timestamp: Date.now(),
                updatedBy: uid,
                message: message
            };
            statusHistory.push(newHistoryEvent);
            const updateData = {
                orderStatus: newStatus,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                statusHistory: statusHistory
            };
            // Calculate ETA logic if transitioning to PREPARING
            if (newStatus === orderStateMachine_1.OrderStatus.PREPARING) {
                updateData.estimatedDeliveryAt = Date.now() + (30 * 60 * 1000); // +30 minutes
            }
            // 3. Perform Update
            transaction.update(orderRef, updateData);
        });
        return { success: true, message: `Order status updated to ${newStatus}` };
    }
    catch (error) {
        console.error("Error updating order status:", error);
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }
        throw new functions.https.HttpsError("internal", "An error occurred while updating the order status.");
    }
});
//# sourceMappingURL=updateOrderStatus.js.map