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
exports.createReview = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
exports.createReview = functions.https.onCall(async (data, context) => {
    // 1. Verify Authentication
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "You must be logged in to create a review.");
    }
    const uid = context.auth.uid;
    // 2. Extract Data
    const { orderId, restaurantId, foodId, rating, comment, userName } = data;
    if (!orderId || !restaurantId || typeof rating !== "number" || rating < 1 || rating > 5) {
        throw new functions.https.HttpsError("invalid-argument", "Missing or invalid review data.");
    }
    if (comment && (comment.trim().length < 5 || comment.trim().length > 1000)) {
        throw new functions.https.HttpsError("invalid-argument", "Comment must be between 5 and 1000 characters.");
    }
    const db = admin.firestore();
    // 3. Verify Order Eligibility
    const orderRef = db.collection("orders").doc(orderId);
    const orderDoc = await orderRef.get();
    if (!orderDoc.exists) {
        throw new functions.https.HttpsError("not-found", "Order not found.");
    }
    const orderData = orderDoc.data();
    if (orderData.userId !== uid) {
        throw new functions.https.HttpsError("permission-denied", "You can only review your own orders.");
    }
    if (orderData.orderStatus !== "DELIVERED") {
        throw new functions.https.HttpsError("failed-precondition", "Order must be DELIVERED to review.");
    }
    if (orderData.restaurantId !== restaurantId) {
        throw new functions.https.HttpsError("invalid-argument", "Order does not match restaurant.");
    }
    let targetType = "RESTAURANT";
    let targetId = restaurantId;
    if (foodId) {
        // Verify food item is actually in the order
        const items = orderData.items || [];
        const hasFood = items.some((item) => item.foodId === foodId);
        if (!hasFood) {
            throw new functions.https.HttpsError("invalid-argument", "Food item not found in order.");
        }
        targetType = "FOOD";
        targetId = foodId;
    }
    // 4. Duplicate Review Check
    // We enforce one review per order-target combination.
    const deterministicReviewId = `${uid}_${orderId}_${targetId}`;
    const reviewRef = db.collection("reviews").doc(deterministicReviewId);
    const existingReview = await reviewRef.get();
    if (existingReview.exists) {
        throw new functions.https.HttpsError("already-exists", "You have already reviewed this item for this order.");
    }
    // 5. Transaction: Create Review and Aggregate
    try {
        await db.runTransaction(async (transaction) => {
            const targetCollection = targetType === "RESTAURANT" ? "restaurants" : "foods";
            const targetRef = db.collection(targetCollection).doc(targetId);
            const targetDoc = await transaction.get(targetRef);
            if (!targetDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Target not found.");
            }
            const targetData = targetDoc.data();
            let currentRatingCount = targetData.ratingCount || 0;
            let currentRatingSum = targetData.ratingSum || 0.0;
            let currentDistribution = targetData.ratingDistribution || { "1": 0, "2": 0, "3": 0, "4": 0, "5": 0 };
            // Calculate new aggregates
            currentRatingCount += 1;
            currentRatingSum += rating;
            const newAverage = currentRatingCount > 0 ? (currentRatingSum / currentRatingCount) : 0.0;
            // Update distribution
            const ratingKey = rating.toString();
            currentDistribution[ratingKey] = (currentDistribution[ratingKey] || 0) + 1;
            // Create Review Object
            const timestamp = Date.now();
            const newReview = {
                reviewId: deterministicReviewId,
                userId: uid,
                userName: userName || "Anonymous",
                orderId: orderId,
                restaurantId: restaurantId,
                foodId: foodId || "",
                rating: rating,
                comment: (comment === null || comment === void 0 ? void 0 : comment.trim()) || "",
                createdAt: timestamp,
                updatedAt: timestamp,
                isEdited: false,
                helpfulCount: 0,
                reportCount: 0
            };
            // Write updates
            transaction.set(reviewRef, newReview);
            transaction.update(targetRef, {
                rating: newAverage,
                ratingCount: currentRatingCount,
                ratingSum: currentRatingSum,
                ratingDistribution: currentDistribution
            });
        });
        return { success: true, reviewId: deterministicReviewId };
    }
    catch (error) {
        throw new functions.https.HttpsError("internal", error.message || "Failed to submit review.");
    }
});
//# sourceMappingURL=createReview.js.map