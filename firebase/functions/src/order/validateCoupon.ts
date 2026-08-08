import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const validateCoupon = functions.https.onCall(async (data, context) => {
    // 1. Authenticate user
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated to validate a coupon.");
    }

    const { couponCode, cartTotal } = data;

    if (!couponCode || typeof couponCode !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "Valid coupon code is required.");
    }

    if (!cartTotal || typeof cartTotal !== "number" || cartTotal <= 0) {
        throw new functions.https.HttpsError("invalid-argument", "Valid cart total is required.");
    }

    const db = admin.firestore();
    
    // Query coupon by code
    const couponQuery = await db.collection("coupons")
        .where("code", "==", couponCode.toUpperCase())
        .limit(1)
        .get();

    if (couponQuery.empty) {
        throw new functions.https.HttpsError("not-found", "Coupon not found.");
    }

    const couponDoc = couponQuery.docs[0];
    const couponData = couponDoc.data();
    
    if (!couponData.isActive) {
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
    
    return {
        couponId: couponDoc.id,
        code: couponData.code,
        discount: discount,
        description: couponData.description,
        isValid: true
    };
});
