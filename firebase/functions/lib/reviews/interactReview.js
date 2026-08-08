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
exports.interactReview = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
exports.interactReview = functions.https.onCall(async (data, context) => {
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
                const reviewData = reviewDoc.data();
                let currentHelpfulCount = reviewData.helpfulCount || 0;
                if (voteDoc.exists) {
                    // User already voted, so we toggle it off (remove vote)
                    transaction.delete(voteRef);
                    currentHelpfulCount = Math.max(0, currentHelpfulCount - 1);
                }
                else {
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
        }
        else if (action === "REPORT") {
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
                const reviewData = reviewDoc.data();
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
    }
    catch (error) {
        throw new functions.https.HttpsError("internal", error.message || "Failed to interact with review.");
    }
});
//# sourceMappingURL=interactReview.js.map