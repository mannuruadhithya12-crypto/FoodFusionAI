import * as functions from "firebase-functions";
import { getRazorpayClient } from "./razorpayClient";
import { convertRupeesToPaise } from "../utils/amount";

export const createRazorpayOrder = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated to create a payment order.");
    }
    
    const { amount, currency = "INR", checkoutReference } = data;

    // 2. Validate amount
    if (!amount || typeof amount !== "number" || amount <= 0) {
        throw new functions.https.HttpsError("invalid-argument", "Valid amount is required.");
    }

    if (currency !== "INR") {
        throw new functions.https.HttpsError("invalid-argument", "Only INR currency is supported.");
    }

    if (!checkoutReference || typeof checkoutReference !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "Valid checkout reference is required.");
    }

    // 3. Create Razorpay order
    const amountInPaise = convertRupeesToPaise(amount);
    
    try {
        const razorpay = getRazorpayClient();
        const options = {
            amount: amountInPaise,
            currency: currency,
            receipt: checkoutReference,
            notes: {
                userId: context.auth.uid
            }
        };

        const order = await razorpay.orders.create(options);

        // 4. Return safe Razorpay order info
        return {
            orderId: order.id,
            amount: order.amount,
            currency: order.currency,
            receipt: order.receipt
        };
    } catch (error) {
        console.error("Error creating Razorpay order:", error);
        throw new functions.https.HttpsError("internal", "Failed to create payment order.");
    }
});
