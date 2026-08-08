import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { getRazorpayClient } from "./razorpayClient";
import { convertRupeesToPaise } from "../utils/amount";

export const createRazorpayOrder = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated to create a payment order.");
    }
    
    const { amount, currency = "INR", checkoutReference, cartTotal, couponId } = data;

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

    // 3. Server-side coupon validation
    let expectedAmount = cartTotal || amount;
    if (couponId) {
        if (!cartTotal) {
            throw new functions.https.HttpsError("invalid-argument", "cartTotal is required when applying a coupon.");
        }
        const db = admin.firestore();
        const couponDoc = await db.collection("coupons").doc(couponId).get();
        
        if (!couponDoc.exists) {
            throw new functions.https.HttpsError("not-found", "Coupon not found.");
        }
        
        const couponData = couponDoc.data();
        if (!couponData?.isActive) {
            throw new functions.https.HttpsError("failed-precondition", "Coupon is inactive.");
        }
        
        if (couponData.validUntil && couponData.validUntil < Date.now()) {
            throw new functions.https.HttpsError("failed-precondition", "Coupon has expired.");
        }
        
        if (couponData.minOrderAmount && cartTotal < couponData.minOrderAmount) {
            throw new functions.https.HttpsError("failed-precondition", `Minimum order amount for this coupon is ₹${couponData.minOrderAmount}.`);
        }
        
        let discount = 0;
        if (couponData.discountPercentage) {
            discount = cartTotal * (couponData.discountPercentage / 100);
            if (couponData.maxDiscountAmount && discount > couponData.maxDiscountAmount) {
                discount = couponData.maxDiscountAmount;
            }
        }
        
        expectedAmount = cartTotal - discount;
    }
    
    // Add delivery fee (assuming fixed ₹50 for now, or match client logic)
    // Actually we should just allow a small margin of error or pass deliveryFee explicitly if we want strictness.
    // To keep it aligned with Phase 6 without breaking, we'll verify the math strictly.
    const deliveryFee = data.deliveryFee || 0;
    expectedAmount += deliveryFee;

    if (Math.abs(expectedAmount - amount) > 0.01) {
        throw new functions.https.HttpsError("invalid-argument", "Amount mismatch. Server calculated a different total.");
    }

    // 4. Create Razorpay order
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
