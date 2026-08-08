import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const editReview = functions.https.onCall(async (data, context) => {
    // 1. Verify Authentication
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "You must be logged in to edit a review.");
    }
    const uid = context.auth.uid;

    // 2. Extract Data
    const { reviewId, rating, comment } = data;

    if (!reviewId || typeof rating !== "number" || rating < 1 || rating > 5) {
        throw new functions.https.HttpsError("invalid-argument", "Missing or invalid review data.");
    }

    if (comment && (comment.trim().length < 5 || comment.trim().length > 1000)) {
        throw new functions.https.HttpsError("invalid-argument", "Comment must be between 5 and 1000 characters.");
    }

    const db = admin.firestore();
    const reviewRef = db.collection("reviews").doc(reviewId);

    // 3. Transaction: Update Review and Aggregate
    try {
        await db.runTransaction(async (transaction) => {
            const reviewDoc = await transaction.get(reviewRef);
            if (!reviewDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Review not found.");
            }
            
            const reviewData = reviewDoc.data()!;
            
            if (reviewData.userId !== uid) {
                throw new functions.https.HttpsError("permission-denied", "You can only edit your own reviews.");
            }

            const oldRating = reviewData.rating;
            const targetType = reviewData.foodId ? "FOOD" : "RESTAURANT";
            const targetId = reviewData.foodId || reviewData.restaurantId;
            const targetCollection = targetType === "RESTAURANT" ? "restaurants" : "foods";
            const targetRef = db.collection(targetCollection).doc(targetId);

            if (oldRating !== rating) {
                const targetDoc = await transaction.get(targetRef);
                if (targetDoc.exists) {
                    const targetData = targetDoc.data()!;
                    let currentRatingCount = targetData.ratingCount || 0;
                    let currentRatingSum = targetData.ratingSum || 0.0;
                    let currentDistribution = targetData.ratingDistribution || { "1": 0, "2": 0, "3": 0, "4": 0, "5": 0 };

                    // Remove old rating
                    currentRatingSum -= oldRating;
                    const oldRatingKey = oldRating.toString();
                    if (currentDistribution[oldRatingKey] > 0) {
                        currentDistribution[oldRatingKey] -= 1;
                    }

                    // Add new rating
                    currentRatingSum += rating;
                    const newRatingKey = rating.toString();
                    currentDistribution[newRatingKey] = (currentDistribution[newRatingKey] || 0) + 1;

                    // Recalculate average (count remains the same since it's an edit)
                    const newAverage = currentRatingCount > 0 ? (currentRatingSum / currentRatingCount) : 0.0;

                    transaction.update(targetRef, {
                        rating: newAverage,
                        ratingSum: currentRatingSum,
                        ratingDistribution: currentDistribution
                    });
                }
            }

            // Update the review itself
            transaction.update(reviewRef, {
                rating: rating,
                comment: comment?.trim() || "",
                updatedAt: Date.now(),
                isEdited: true
            });
        });

        return { success: true };
    } catch (error: any) {
        throw new functions.https.HttpsError("internal", error.message || "Failed to edit review.");
    }
});
