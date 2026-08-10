import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const declineDeliveryAssignment = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Auth required");
    }

    const { offerId } = data;
    if (!offerId) {
        throw new functions.https.HttpsError("invalid-argument", "Offer ID required");
    }

    const uid = context.auth.uid;
    const db = admin.firestore();
    const offerRef = db.collection("driverOffers").doc(offerId);

    try {
        await db.runTransaction(async (transaction) => {
            const offerDoc = await transaction.get(offerRef);
            if (!offerDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Offer not found");
            }

            const offer = offerDoc.data()!;
            if (offer.driverId !== uid) {
                throw new functions.https.HttpsError("permission-denied", "Offer is not assigned to you");
            }

            transaction.update(offerRef, { status: "DECLINED" });

            // Create decline audit log
            const logRef = db.collection("auditLogs").doc();
            transaction.set(logRef, {
                logId: logRef.id,
                actorUid: uid,
                actorRole: "DELIVERY_PARTNER",
                action: "DRIVER_DECLINED",
                orderId: offer.orderId,
                driverId: uid,
                timestamp: admin.firestore.FieldValue.serverTimestamp()
            });
        });

        return { success: true, message: "Offer declined successfully" };
    } catch (error: any) {
        console.error("Error in declineDeliveryAssignment:", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal error");
    }
});
