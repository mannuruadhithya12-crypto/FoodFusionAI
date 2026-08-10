import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const bootstrapAdmin = functions.https.onCall(async (data, context) => {
    // SECURITY WARNING: In a real production app, this function MUST be protected by a hardcoded secret
    // or run purely from the Firebase CLI / Admin SDK in a secure backend environment.
    // We are exposing it temporarily for the sake of Phase 11 initialization, but protecting it with a secret code.
    
    const { secret, email } = data;

    if (secret !== "FOODFUSION_ADMIN_INIT_2026") {
        throw new functions.https.HttpsError("permission-denied", "Invalid bootstrap secret");
    }

    if (!email) {
        throw new functions.https.HttpsError("invalid-argument", "Email is required");
    }

    try {
        const user = await admin.auth().getUserByEmail(email);
        
        // Set custom claims
        await admin.auth().setCustomUserClaims(user.uid, { admin: true });

        // Add to adminUsers collection
        await admin.firestore().collection("adminUsers").doc(user.uid).set({
            email: user.email,
            role: "SUPER_ADMIN",
            promotedAt: admin.firestore.FieldValue.serverTimestamp()
        });

        return { success: true, message: `User ${email} successfully promoted to Admin.` };
    } catch (error: any) {
        throw new functions.https.HttpsError("internal", error.message);
    }
});
