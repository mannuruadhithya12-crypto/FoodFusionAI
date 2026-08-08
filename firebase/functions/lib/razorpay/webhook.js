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
exports.razorpayWebhook = void 0;
const functions = __importStar(require("firebase-functions"));
const crypto = __importStar(require("crypto"));
const admin = __importStar(require("firebase-admin"));
const params_1 = require("firebase-functions/params");
const webhookSecret = (0, params_1.defineString)("RAZORPAY_WEBHOOK_SECRET");
exports.razorpayWebhook = functions.https.onRequest(async (req, res) => {
    try {
        const signature = req.headers["x-razorpay-signature"];
        if (!signature) {
            console.error("Missing signature");
            res.status(400).send("Missing signature");
            return;
        }
        // Use raw body for signature verification
        const rawBody = req.rawBody.toString();
        const secret = webhookSecret.value();
        const expectedSignature = crypto
            .createHmac("sha256", secret)
            .update(rawBody)
            .digest("hex");
        if (expectedSignature !== signature) {
            console.error("Invalid signature");
            res.status(400).send("Invalid signature");
            return;
        }
        // Signature verified, process the event
        const event = req.body;
        const eventId = event.id;
        if (!eventId) {
            console.error("Missing event ID");
            res.status(400).send("Missing event ID");
            return;
        }
        const db = admin.firestore();
        const processedEventsRef = db.collection("processed_webhooks").doc(eventId);
        // Run in transaction for idempotency
        await db.runTransaction(async (transaction) => {
            var _a, _b;
            const eventDoc = await transaction.get(processedEventsRef);
            if (eventDoc.exists) {
                // Event already processed
                console.log("Event already processed", eventId);
                return;
            }
            const eventType = event.event;
            const payload = event.payload;
            if (eventType === "payment.captured" || eventType === "order.paid") {
                const payment = (_a = payload.payment) === null || _a === void 0 ? void 0 : _a.entity;
                const orderId = payment === null || payment === void 0 ? void 0 : payment.order_id;
                if (orderId) {
                    // Update order status in Firestore
                    const ordersRef = db.collection("orders").where("paymentReference", "==", orderId);
                    const ordersSnapshot = await transaction.get(ordersRef);
                    if (!ordersSnapshot.empty) {
                        ordersSnapshot.forEach(doc => {
                            transaction.update(doc.ref, {
                                paymentStatus: "SUCCESS",
                                orderStatus: "CONFIRMED",
                                updatedAt: admin.firestore.FieldValue.serverTimestamp()
                            });
                        });
                    }
                }
            }
            else if (eventType === "payment.failed") {
                const payment = (_b = payload.payment) === null || _b === void 0 ? void 0 : _b.entity;
                const orderId = payment === null || payment === void 0 ? void 0 : payment.order_id;
                if (orderId) {
                    const ordersRef = db.collection("orders").where("paymentReference", "==", orderId);
                    const ordersSnapshot = await transaction.get(ordersRef);
                    if (!ordersSnapshot.empty) {
                        ordersSnapshot.forEach(doc => {
                            transaction.update(doc.ref, {
                                paymentStatus: "FAILED",
                                updatedAt: admin.firestore.FieldValue.serverTimestamp()
                            });
                        });
                    }
                }
            }
            // Mark event as processed
            transaction.set(processedEventsRef, {
                processedAt: admin.firestore.FieldValue.serverTimestamp(),
                type: eventType
            });
        });
        res.status(200).send({ status: "ok" });
    }
    catch (error) {
        console.error("Webhook processing error:", error);
        res.status(500).send("Internal server error");
    }
});
//# sourceMappingURL=webhook.js.map