import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const approveDriver = functions.https.onCall(async (data, context) => {
    // 1. Authenticate and check Admin claims
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be logged in to approve drivers");
    }

    const callerUid = context.auth.uid;
    const db = admin.firestore();

    // Check if admin claim is present or exists in adminUsers
    const isAdminToken = context.auth.token.admin === true;
    const adminUserDoc = await db.collection("adminUsers").doc(callerUid).get();

    if (!isAdminToken && !adminUserDoc.exists) {
        throw new functions.https.HttpsError("permission-denied", "Only administrators can approve drivers");
    }

    const { driverId } = data;
    if (!driverId) {
        throw new functions.https.HttpsError("invalid-argument", "Driver ID is required");
    }

    const driverRef = db.collection("drivers").doc(driverId);

    try {
        await db.runTransaction(async (transaction) => {
            const driverDoc = await transaction.get(driverRef);
            if (!driverDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Driver profile not found");
            }

            const driverData = driverDoc.data()!;
            const previousStatus = driverData.status;

            // Transition to APPROVED
            transaction.update(driverRef, {
                status: "APPROVED",
                availability: "OFFLINE", // Start offline until they toggle online
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });

            // Set custom user claims for role DELIVERY_PARTNER
            await admin.auth().setCustomUserClaims(driverId, {
                role: "DELIVERY_PARTNER"
            });

            // Write immutable audit log
            const logRef = db.collection("auditLogs").doc();
            transaction.set(logRef, {
                logId: logRef.id,
                actorUid: callerUid,
                actorRole: "SUPER_ADMIN",
                action: "DRIVER_APPROVED",
                driverId: driverId,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                before: { status: previousStatus },
                after: { status: "APPROVED" }
            });

            // Notify driver of approval if FCM token exists
            if (driverData.fcmToken) {
                const approvalMsg = {
                    notification: {
                        title: "Account Approved!",
                        body: "Congratulations, your FoodFusion driver account has been approved. Go online to start earning!"
                    },
                    data: {
                        type: "DRIVER_APPROVAL",
                        status: "APPROVED",
                        targetId: driverId
                    },
                    token: driverData.fcmToken
                };
                await admin.messaging().send(approvalMsg).catch(console.error);
            }
        });

        return { success: true, message: "Driver successfully approved and authorized" };
    } catch (error: any) {
        console.error("Error approving driver:", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal error");
    }
});
export const suspendDriver = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be logged in to suspend drivers");
    }

    const callerUid = context.auth.uid;
    const db = admin.firestore();

    const isAdminToken = context.auth.token.admin === true;
    const adminUserDoc = await db.collection("adminUsers").doc(callerUid).get();

    if (!isAdminToken && !adminUserDoc.exists) {
        throw new functions.https.HttpsError("permission-denied", "Only administrators can suspend drivers");
    }

    const { driverId, suspend } = data; // suspend: boolean
    if (!driverId) {
        throw new functions.https.HttpsError("invalid-argument", "Driver ID is required");
    }

    const driverRef = db.collection("drivers").doc(driverId);
    const newStatus = suspend ? "SUSPENDED" : "APPROVED";

    try {
        await db.runTransaction(async (transaction) => {
            const driverDoc = await transaction.get(driverRef);
            if (!driverDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Driver profile not found");
            }

            const driverData = driverDoc.data()!;
            const previousStatus = driverData.status;

            transaction.update(driverRef, {
                status: newStatus,
                availability: suspend ? "OFFLINE" : "OFFLINE",
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });

            // Write audit log
            const logRef = db.collection("auditLogs").doc();
            transaction.set(logRef, {
                logId: logRef.id,
                actorUid: callerUid,
                actorRole: "SUPER_ADMIN",
                action: suspend ? "DRIVER_SUSPENDED" : "DRIVER_REACTIVATED",
                driverId: driverId,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                before: { status: previousStatus },
                after: { status: newStatus }
            });
        });

        return { success: true, message: `Driver status successfully updated to ${newStatus}` };
    } catch (error: any) {
        console.error("Error changing driver status:", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal error");
    }
});
