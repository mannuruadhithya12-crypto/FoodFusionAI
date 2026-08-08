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
exports.setDefaultAddress = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
/**
 * Callable function to safely set a default address for a user.
 * Expects { addressId: string } in data payload.
 */
exports.setDefaultAddress = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "You must be logged in to set a default address.");
    }
    const uid = context.auth.uid;
    const { addressId } = data;
    if (!addressId || typeof addressId !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "The function must be called with an 'addressId'.");
    }
    const db = admin.firestore();
    const addressesRef = db.collection("users").doc(uid).collection("addresses");
    try {
        await db.runTransaction(async (transaction) => {
            // Read the target address to ensure it exists
            const targetRef = addressesRef.doc(addressId);
            const targetDoc = await transaction.get(targetRef);
            if (!targetDoc.exists) {
                throw new functions.https.HttpsError("not-found", "The specified address does not exist.");
            }
            // Read all existing default addresses for the user
            const currentDefaultsSnapshot = await transaction.get(addressesRef.where("isDefault", "==", true));
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
    }
    catch (error) {
        throw new functions.https.HttpsError("internal", error.message || "Failed to set default address.");
    }
});
//# sourceMappingURL=setDefaultAddress.js.map