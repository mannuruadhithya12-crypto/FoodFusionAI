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
exports.createRazorpayOrder = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
const razorpayClient_1 = require("./razorpayClient");
const amount_1 = require("../utils/amount");
exports.createRazorpayOrder = functions.https.onCall(async (data, context) => {
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
        if (!(couponData === null || couponData === void 0 ? void 0 : couponData.isActive)) {
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
    const amountInPaise = (0, amount_1.convertRupeesToPaise)(amount);
    try {
        const razorpay = (0, razorpayClient_1.getRazorpayClient)();
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
    }
    catch (error) {
        console.error("Error creating Razorpay order:", error);
        throw new functions.https.HttpsError("internal", "Failed to create payment order.");
    }
});
//# sourceMappingURL=createOrder.js.map