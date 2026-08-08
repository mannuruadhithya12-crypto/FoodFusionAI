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
exports.verifyRazorpayPayment = void 0;
const functions = __importStar(require("firebase-functions"));
const crypto = __importStar(require("crypto"));
const razorpayClient_1 = require("./razorpayClient");
exports.verifyRazorpayPayment = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated to verify payment.");
    }
    const { paymentId, signature, checkoutReference } = data;
    if (!paymentId || !signature || !checkoutReference) {
        throw new functions.https.HttpsError("invalid-argument", "Missing required payment verification data.");
    }
    try {
        const razorpay = (0, razorpayClient_1.getRazorpayClient)();
        // 2. Fetch the payment details from Razorpay securely
        const payment = await razorpay.payments.fetch(paymentId);
        if (!payment) {
            throw new functions.https.HttpsError("not-found", "Payment not found.");
        }
        const trustedOrderId = payment.order_id;
        if (!trustedOrderId) {
            throw new functions.https.HttpsError("failed-precondition", "Payment is not associated with an order.");
        }
        // 3. Verify Signature using the trusted order ID
        const secret = (0, razorpayClient_1.getRazorpaySecret)();
        const payload = trustedOrderId + "|" + paymentId;
        const expectedSignature = crypto
            .createHmac("sha256", secret)
            .update(payload)
            .digest("hex");
        if (expectedSignature !== signature) {
            console.error("Signature mismatch. Expected:", expectedSignature, "Received:", signature);
            throw new functions.https.HttpsError("permission-denied", "Invalid payment signature.");
        }
        // 4. Verify Payment Status
        if (payment.status !== "captured") {
            console.error("Payment status is not captured. Current status:", payment.status);
            throw new functions.https.HttpsError("failed-precondition", "Payment is not captured yet.");
        }
        // Optional: Could verify amount matches the internal checkoutReference expectation if stored in DB.
        // Return successful verification
        return {
            verified: true,
            orderId: trustedOrderId,
            amount: payment.amount,
            currency: payment.currency,
            status: payment.status
        };
    }
    catch (error) {
        console.error("Error verifying payment:", error);
        // Do not leak internal error details to client
        throw new functions.https.HttpsError("internal", "Verification failed due to an internal error.");
    }
});
//# sourceMappingURL=verifyPayment.js.map