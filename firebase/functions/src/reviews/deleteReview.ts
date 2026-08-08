import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const deleteReview = functions.https.onCall(async (data, context) => {
    // 1. Verify Authentication
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "You must be logged in to delete a review.");
    }
    const uid = context.auth.uid;

    // 2. Extract Data
    const { reviewId } = data;

    if (!reviewId) {
        throw new functions.https.HttpsError("invalid-argument", "Missing review ID.");
    }

    const db = admin.firestore();
    const reviewRef = db.collection("reviews").doc(reviewId);

    // 3. Transaction: Delete Review and Aggregate
    try {
        await db.runTransaction(async (transaction) => {
            const reviewDoc = await transaction.get(reviewRef);
            if (!reviewDoc.exists) {
                // If it doesn't exist, we can consider it a success (idempotent)
                return;
            }
            
            const reviewData = reviewDoc.data()!;
            
            if (reviewData.userId !== uid) {
                throw new functions.https.HttpsError("permission-denied", "You can only delete your own reviews.");
            }

            const oldRating = reviewData.rating;
            const targetType = reviewData.foodId ? "FOOD" : "RESTAURANT";
            const targetId = reviewData.foodId || reviewData.restaurantId;
            const targetCollection = targetType === "RESTAURANT" ? "restaurants" : "foods";
            const targetRef = db.collection(targetCollection).doc(targetId);

            const targetDoc = await transaction.get(targetRef);
            if (targetDoc.exists) {
                const targetData = targetDoc.data()!;
                let currentRatingCount = targetData.ratingCount || 0;
                let currentRatingSum = targetData.ratingSum || 0.0;
                let currentDistribution = targetData.ratingDistribution || { "1": 0, "2": 0, "3": 0, "4": 0, "5": 0 };

                // Remove old rating
                currentRatingCount = Math.max(0, currentRatingCount - 1);
                currentRatingSum = Math.max(0.0, currentRatingSum - oldRating);
                
                const oldRatingKey = oldRating.toString();
                if (currentDistribution[oldRatingKey] > 0) {
                    currentDistribution[oldRatingKey] -= 1;
                }

                // Recalculate average
                const newAverage = currentRatingCount > 0 ? (currentRatingSum / currentRatingCount) : 0.0;

                transaction.update(targetRef, {
                    rating: newAverage,
                    ratingCount: currentRatingCount,
                    ratingSum: currentRatingSum,
                    ratingDistribution: currentDistribution
                });
            }

            // Delete the review
            transaction.delete(reviewRef);
        });

        return { success: true };
    } catch (error: any) {
        throw new functions.https.HttpsError("internal", error.message || "Failed to delete review.");
    }
});
