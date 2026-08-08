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
exports.deleteUserAccount = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
/**
 * Callable function to safely delete a user's account and all associated data.
 */
exports.deleteUserAccount = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "You must be logged in to delete your account.");
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
    }
    catch (error) {
        throw new functions.https.HttpsError("internal", error.message || "Failed to delete account.");
    }
});
//# sourceMappingURL=deleteUserAccount.js.map