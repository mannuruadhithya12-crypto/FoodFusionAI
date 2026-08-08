import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

/**
 * Callable function to safely set a default address for a user.
 * Expects { addressId: string } in data payload.
 */
export const setDefaultAddress = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "You must be logged in to set a default address."
        );
    }

    const uid = context.auth.uid;
    const { addressId } = data;

    if (!addressId || typeof addressId !== "string") {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "The function must be called with an 'addressId'."
        );
    }

    const db = admin.firestore();
    const addressesRef = db.collection("users").doc(uid).collection("addresses");

    try {
        await db.runTransaction(async (transaction) => {
            // Read the target address to ensure it exists
            const targetRef = addressesRef.doc(addressId);
            const targetDoc = await transaction.get(targetRef);

            if (!targetDoc.exists) {
                throw new functions.https.HttpsError(
                    "not-found",
                    "The specified address does not exist."
                );
            }

            // Read all existing default addresses for the user
            const currentDefaultsSnapshot = await transaction.get(
                addressesRef.where("isDefault", "==", true)
            );

            // Remove default status from all currently default addresses
            currentDefaultsSnapshot.forEach((doc) => {
                transaction.update(doc.ref, { 
                    isDefault: false,
                    updatedAt: admin.firestore.FieldValue.serverTimestamp()
                });
            });

            // Set new default
            transaction.update(targetRef, { 
                isDefault: true,
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
        });

        return { success: true, message: "Default address updated successfully" };
    } catch (error: any) {
        throw new functions.https.HttpsError(
            "internal",
            error.message || "Failed to set default address."
        );
    }
});
