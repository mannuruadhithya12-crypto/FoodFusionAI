import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { issueOrderRewards } from "../utils/rewardSystem";

// Simple helpers to verify partner role and ownership of the restaurant
async function verifyPartnerAccess(uid: string, restaurantId: string) {
    const userDoc = await admin.firestore().collection("users").doc(uid).get();
    if (!userDoc.exists) {
        throw new functions.https.HttpsError("permission-denied", "User profile not found.");
    }
    const userData = userDoc.data();
    if (!userData || !userData.active) {
        throw new functions.https.HttpsError("permission-denied", "User account is suspended or inactive.");
    }
    const role = userData.role;
    const allowedRoles = ["RESTAURANT_OWNER", "RESTAURANT_MANAGER", "RESTAURANT_STAFF", "SUPER_ADMIN"];
    if (!allowedRoles.includes(role)) {
        throw new functions.https.HttpsError("permission-denied", "Unauthorized role for restaurant partner operations.");
    }
    if (role !== "SUPER_ADMIN") {
        const restaurantIds = userData.restaurantIds || [];
        if (!restaurantIds.includes(restaurantId)) {
            throw new functions.https.HttpsError("permission-denied", "User does not belong to this restaurant.");
        }
    }
    return userData;
}

// 1. Accept Order Cloud Function
export const partnerAcceptOrder = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
    }
    const { orderId, prepMinutes } = data;
    if (!orderId || !prepMinutes) {
        throw new functions.https.HttpsError("invalid-argument", "orderId and prepMinutes are required.");
    }

    const db = admin.firestore();
    const orderRef = db.collection("orders").doc(orderId);

    try {
        await db.runTransaction(async (transaction) => {
            const orderDoc = await transaction.get(orderRef);
            if (!orderDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Order not found.");
            }
            const orderData = orderDoc.data();
            const restaurantId = orderData?.restaurantId;

            // Verify role and restaurant membership
            await verifyPartnerAccess(context.auth!.uid, restaurantId);

            const currentStatus = orderData?.orderStatus;
            if (currentStatus !== "CONFIRMED") {
                throw new functions.https.HttpsError("failed-precondition", `Cannot accept order in ${currentStatus} state.`);
            }

            const now = Date.now();
            const prepMs = prepMinutes * 60 * 1000;
            const estimatedReadyAt = now + prepMs;

            const statusHistory = orderData?.statusHistory || [];
            statusHistory.push({
                status: "PREPARING",
                previousStatus: currentStatus,
                timestamp: now,
                updatedBy: context.auth!.uid,
                message: `Order accepted. Estimated prep time: ${prepMinutes} minutes.`
            });

            // Update order properties
            transaction.update(orderRef, {
                orderStatus: "PREPARING",
                acceptedAt: now,
                estimatedPreparationMinutes: prepMinutes,
                estimatedReadyAt: estimatedReadyAt,
                estimatedDeliveryAt: estimatedReadyAt + (15 * 60 * 1000), // +15 mins buffer for driver
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                statusHistory: statusHistory
            });

            // Write append-only Audit Log
            const auditRef = db.collection("auditLogs").doc();
            transaction.set(auditRef, {
                actorUid: context.auth!.uid,
                restaurantId: restaurantId,
                action: "ORDER_ACCEPTED",
                targetId: orderId,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                changes: { previousStatus: currentStatus, currentStatus: "PREPARING", prepMinutes }
            });
        });

        return { success: true, message: "Order accepted successfully." };
    } catch (error: any) {
        console.error("partnerAcceptOrder failed", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal failure.");
    }
});

// 2. Reject Order Cloud Function
export const partnerRejectOrder = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
    }
    const { orderId, reason } = data;
    if (!orderId || !reason) {
        throw new functions.https.HttpsError("invalid-argument", "orderId and reason are required.");
    }

    const db = admin.firestore();
    const orderRef = db.collection("orders").doc(orderId);

    try {
        await db.runTransaction(async (transaction) => {
            const orderDoc = await transaction.get(orderRef);
            if (!orderDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Order not found.");
            }
            const orderData = orderDoc.data();
            const restaurantId = orderData?.restaurantId;

            // Verify role and restaurant membership
            await verifyPartnerAccess(context.auth!.uid, restaurantId);

            const currentStatus = orderData?.orderStatus;
            // Orders can be rejected if confirmed or pending payment
            const rejectableStates = ["CONFIRMED", "PENDING_PAYMENT", "PAYMENT_PROCESSING"];
            if (!rejectableStates.includes(currentStatus)) {
                throw new functions.https.HttpsError("failed-precondition", `Cannot reject order in ${currentStatus} state.`);
            }

            const now = Date.now();
            const statusHistory = orderData?.statusHistory || [];
            statusHistory.push({
                status: "CANCELLED",
                previousStatus: currentStatus,
                timestamp: now,
                updatedBy: context.auth!.uid,
                message: `Order rejected by restaurant: ${reason}`
            });

            // Update order properties
            transaction.update(orderRef, {
                orderStatus: "CANCELLED",
                cancellationReason: reason,
                cancelledAt: now,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                statusHistory: statusHistory
            });

            // Write append-only Audit Log
            const auditRef = db.collection("auditLogs").doc();
            transaction.set(auditRef, {
                actorUid: context.auth!.uid,
                restaurantId: restaurantId,
                action: "ORDER_REJECTED",
                targetId: orderId,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                changes: { previousStatus: currentStatus, currentStatus: "CANCELLED", reason }
            });
        });

        return { success: true, message: "Order rejected successfully." };
    } catch (error: any) {
        console.error("partnerRejectOrder failed", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal failure.");
    }
});

// 3. Update Order Status Cloud Function
export const partnerUpdateOrderStatus = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
    }
    const { orderId, newStatus } = data;
    if (!orderId || !newStatus) {
        throw new functions.https.HttpsError("invalid-argument", "orderId and newStatus are required.");
    }

    const db = admin.firestore();
    const orderRef = db.collection("orders").doc(orderId);

    try {
        await db.runTransaction(async (transaction) => {
            const orderDoc = await transaction.get(orderRef);
            if (!orderDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Order not found.");
            }
            const orderData = orderDoc.data();
            const restaurantId = orderData?.restaurantId;

            // Verify role and restaurant membership
            await verifyPartnerAccess(context.auth!.uid, restaurantId);

            const currentStatus = orderData?.orderStatus;
            
            // Validate transition rules
            let isValid = false;
            if (currentStatus === "PREPARING" && newStatus === "READY_FOR_PICKUP") isValid = true;
            if (currentStatus === "READY_FOR_PICKUP" && newStatus === "COMPLETED") isValid = true; // For self-pickup or simulated completed

            if (!isValid) {
                throw new functions.https.HttpsError("failed-precondition", `Invalid state transition from ${currentStatus} to ${newStatus}.`);
            }

            const now = Date.now();
            const statusHistory = orderData?.statusHistory || [];
            statusHistory.push({
                status: newStatus,
                previousStatus: currentStatus,
                timestamp: now,
                updatedBy: context.auth!.uid,
                message: `Order status updated to ${newStatus}.`
            });

            // Update order properties
            transaction.update(orderRef, {
                orderStatus: newStatus,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                statusHistory: statusHistory
            });

            // Write append-only Audit Log
            const auditRef = db.collection("auditLogs").doc();
            transaction.set(auditRef, {
                actorUid: context.auth!.uid,
                restaurantId: restaurantId,
                action: `ORDER_${newStatus}`,
                targetId: orderId,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                changes: { previousStatus: currentStatus, currentStatus: newStatus }
            });

            if (newStatus === "COMPLETED") {
                const totalAmount = orderData?.totalAmount || 0;
                await issueOrderRewards(db, transaction, orderData?.userId, orderId, totalAmount);
            }
        });

        return { success: true, message: `Order status updated to ${newStatus} successfully.` };
    } catch (error: any) {
        console.error("partnerUpdateOrderStatus failed", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal failure.");
    }
});

// 4. Invite Restaurant Staff Cloud Function
export const inviteRestaurantStaff = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
    }
    const { email, role, restaurantId } = data;
    if (!email || !role || !restaurantId) {
        throw new functions.https.HttpsError("invalid-argument", "email, role, and restaurantId are required.");
    }

    // Verify caller is Owner of the restaurant
    const callerDoc = await admin.firestore().collection("users").doc(context.auth.uid).get();
    const callerData = callerDoc.data();
    if (!callerData || callerData.role !== "RESTAURANT_OWNER" || !callerData.restaurantIds?.includes(restaurantId)) {
         throw new functions.https.HttpsError("permission-denied", "Only restaurant owners can invite staff.");
    }

    const db = admin.firestore();
    try {
        // Find existing user by email
        let uid = "";
        try {
            const userRecord = await admin.auth().getUserByEmail(email);
            uid = userRecord.uid;
        } catch (e: any) {
             // User does not exist in Auth, we can pre-create their record in users collection by email, 
             // so when they signup, they inherit these restaurant parameters.
        }

        if (uid) {
             const userRef = db.collection("users").doc(uid);
             await db.runTransaction(async (transaction) => {
                 const doc = await transaction.get(userRef);
                 const currentData = doc.data();
                 const currentIds = currentData?.restaurantIds || [];
                 if (!currentIds.includes(restaurantId)) {
                     currentIds.push(restaurantId);
                 }
                 transaction.set(userRef, {
                     uid: uid,
                     email: email,
                     role: role,
                     restaurantIds: currentIds,
                     active: true,
                     updatedAt: admin.firestore.FieldValue.serverTimestamp()
                 }, { merge: true });

                 // Log audit trail
                 const auditRef = db.collection("auditLogs").doc();
                 transaction.set(auditRef, {
                     actorUid: context.auth!.uid,
                     restaurantId: restaurantId,
                     action: "STAFF_INVITED",
                     targetId: uid,
                     timestamp: admin.firestore.FieldValue.serverTimestamp(),
                     changes: { email, role }
                 });
             });
        } else {
             // Pre-onboard by email in users collection (queried by email during signup)
             const pendingUserRef = db.collection("users").doc();
             await pendingUserRef.set({
                 email: email,
                 role: role,
                 restaurantIds: [restaurantId],
                 active: true,
                 isPendingInvitation: true,
                 createdAt: admin.firestore.FieldValue.serverTimestamp()
             });
        }

        return { success: true, message: `Staff ${email} invited successfully.` };
    } catch (error: any) {
        console.error("inviteRestaurantStaff failed", error);
        throw new functions.https.HttpsError("internal", error.message || "Internal error inviting staff.");
    }
});

// 5. Remove Restaurant Staff Cloud Function
export const removeRestaurantStaff = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
    }
    const { targetUid, restaurantId } = data;
    if (!targetUid || !restaurantId) {
        throw new functions.https.HttpsError("invalid-argument", "targetUid and restaurantId are required.");
    }

    // Verify caller is Owner of the restaurant
    const callerDoc = await admin.firestore().collection("users").doc(context.auth.uid).get();
    const callerData = callerDoc.data();
    if (!callerData || callerData.role !== "RESTAURANT_OWNER" || !callerData.restaurantIds?.includes(restaurantId)) {
         throw new functions.https.HttpsError("permission-denied", "Only restaurant owners can remove staff.");
    }

    if (targetUid === context.auth.uid) {
         throw new functions.https.HttpsError("invalid-argument", "Owners cannot remove themselves from their own staff.");
    }

    const db = admin.firestore();
    const userRef = db.collection("users").doc(targetUid);

    try {
        await db.runTransaction(async (transaction) => {
            const doc = await transaction.get(userRef);
            if (!doc.exists) {
                throw new functions.https.HttpsError("not-found", "User not found.");
            }
            const data = doc.data();
            const restaurantIds: string[] = data?.restaurantIds || [];
            const updatedIds = restaurantIds.filter(id => id !== restaurantId);

            transaction.update(userRef, {
                restaurantIds: updatedIds,
                // If they have no restaurants left, we deactivate them
                active: updatedIds.length > 0,
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });

            // Log audit trail
            const auditRef = db.collection("auditLogs").doc();
            transaction.set(auditRef, {
                actorUid: context.auth!.uid,
                restaurantId: restaurantId,
                action: "STAFF_REMOVED",
                targetId: targetUid,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                changes: { removedUid: targetUid }
            });
        });

        return { success: true, message: "Staff member removed successfully." };
    } catch (error: any) {
        console.error("removeRestaurantStaff failed", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal error removing staff.");
    }
});
