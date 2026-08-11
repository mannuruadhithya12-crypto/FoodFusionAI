import * as admin from "firebase-admin";

/**
 * Issues reward points based on order total securely within a transaction.
 */
export const issueOrderRewards = async (
    db: admin.firestore.Firestore,
    transaction: admin.firestore.Transaction,
    userId: string,
    orderId: string,
    totalAmount: number
) => {
    const earnedPoints = Math.floor(totalAmount / 100);
    if (earnedPoints <= 0) return;

    // Use idempotency key for rewards to prevent double-issuing
    const rewardLogRef = db.collection("users").doc(userId).collection("rewardLogs").doc(orderId);
    const rewardLogDoc = await transaction.get(rewardLogRef);

    if (rewardLogDoc.exists) {
        console.log(`Rewards already issued for order ${orderId}`);
        return;
    }

    // Update user balance
    const userRef = db.collection("users").doc(userId);
    transaction.update(userRef, {
        rewardBalance: admin.firestore.FieldValue.increment(earnedPoints)
    });

    // Log the reward issuance
    transaction.set(rewardLogRef, {
        orderId: orderId,
        points: earnedPoints,
        type: "EARNED_FROM_ORDER",
        timestamp: admin.firestore.FieldValue.serverTimestamp()
    });

    console.log(`Issued ${earnedPoints} reward points to user ${userId} for order ${orderId}`);
};
