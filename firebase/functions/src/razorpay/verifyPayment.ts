import * as functions from "firebase-functions";
import * as crypto from "crypto";
import { getRazorpayClient, getRazorpaySecret } from "./razorpayClient";

export const verifyRazorpayPayment = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated to verify payment.");
    }

    const { paymentId, signature, checkoutReference } = data;

    if (!paymentId || !signature || !checkoutReference) {
        throw new functions.https.HttpsError("invalid-argument", "Missing required payment verification data.");
    }

    try {
        const razorpay = getRazorpayClient();
        
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
        const secret = getRazorpaySecret();
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
        
    } catch (error) {
        console.error("Error verifying payment:", error);
        // Do not leak internal error details to client
        throw new functions.https.HttpsError("internal", "Verification failed due to an internal error.");
    }
});
