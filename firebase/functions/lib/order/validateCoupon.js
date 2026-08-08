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
exports.validateCoupon = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
exports.validateCoupon = functions.https.onCall(async (data, context) => {
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
//# sourceMappingURL=validateCoupon.js.map