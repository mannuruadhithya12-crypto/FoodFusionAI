import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const interactReview = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "You must be logged in to interact with reviews.");
    }
    const uid = context.auth.uid;
    const { reviewId, action, reason } = data;

    if (!reviewId || !action || !["HELPFUL", "REPORT"].includes(action)) {
        throw new functions.https.HttpsError("invalid-argument", "Invalid interaction data.");
    }

    const db = admin.firestore();
    const reviewRef = db.collection("reviews").doc(reviewId);

    try {
        if (action === "HELPFUL") {
            // One vote per user, so we use a deterministic ID
            const voteRef = db.collection("users").doc(uid).collection("reviewVotes").doc(reviewId);
            
            await db.runTransaction(async (transaction) => {
                const reviewDoc = await transaction.get(reviewRef);
                if (!reviewDoc.exists) {
                    throw new functions.https.HttpsError("not-found", "Review not found.");
                }

                const voteDoc = await transaction.get(voteRef);
                const reviewData = reviewDoc.data()!;
                let currentHelpfulCount = reviewData.helpfulCount || 0;

                if (voteDoc.exists) {
                    // User already voted, so we toggle it off (remove vote)
                    transaction.delete(voteRef);
                    currentHelpfulCount = Math.max(0, currentHelpfulCount - 1);
                } else {
                    // User hasn't voted, toggle it on (add vote)
                    transaction.set(voteRef, {
                        reviewId: reviewId,
                        timestamp: Date.now()
                    });
                    currentHelpfulCount += 1;
                }

                transaction.update(reviewRef, { helpfulCount: currentHelpfulCount });
            });
            return { success: true };
        } else if (action === "REPORT") {
            const reportId = `${uid}_${reviewId}`;
            const reportRef = db.collection("reports").doc(reportId);

            await db.runTransaction(async (transaction) => {
                const reportDoc = await transaction.get(reportRef);
                if (reportDoc.exists) {
                    throw new functions.https.HttpsError("already-exists", "You have already reported this review.");
                }

                const reviewDoc = await transaction.get(reviewRef);
                if (!reviewDoc.exists) {
                    throw new functions.https.HttpsError("not-found", "Review not found.");
                }

                const reviewData = reviewDoc.data()!;
                let currentReportCount = reviewData.reportCount || 0;
                
                transaction.set(reportRef, {
                    reportId: reportId,
                    reviewId: reviewId,
                    reporterId: uid,
                    reason: reason || "Other",
                    createdAt: Date.now()
                });

                transaction.update(reviewRef, { reportCount: currentReportCount + 1 });
            });
            return { success: true };
        }
        return { success: false, reason: "Unhandled action" };
    } catch (error: any) {
        throw new functions.https.HttpsError("internal", error.message || "Failed to interact with review.");
    }
});
