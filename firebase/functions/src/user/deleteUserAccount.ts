import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

/**
 * Callable function to safely delete a user's account and all associated data.
 */
export const deleteUserAccount = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "You must be logged in to delete your account."
        );
    }

    const uid = context.auth.uid;
    const db = admin.firestore();

    try {
        // We use a batched approach or sequential deletions since this is a cleanup operation.
        // In a production app with massive data, you'd use a background queue or recursive delete.
        // For this scale, we manually delete known subcollections/docs.

        // 1. Delete Addresses
        const addressesSnapshot = await db.collection("users").doc(uid).collection("addresses").get();
        const addressBatch = db.batch();
        addressesSnapshot.forEach((doc) => {
            addressBatch.delete(doc.ref);
        });
        await addressBatch.commit();

        // 2. Delete User Profile
        await db.collection("users").doc(uid).delete();

        // 3. Delete Carts / Favorites if they exist (based on current rules structure)
        const cartSnapshot = await db.collection("carts").where("userId", "==", uid).get();
        const cartBatch = db.batch();
        cartSnapshot.forEach((doc) => {
            cartBatch.delete(doc.ref);
        });
        await cartBatch.commit();

        // 4. Finally, delete the Auth Record
        await admin.auth().deleteUser(uid);

        return { success: true, message: "Account deleted successfully." };
    } catch (error: any) {
        throw new functions.https.HttpsError(
            "internal",
            error.message || "Failed to delete account."
        );
    }
});
