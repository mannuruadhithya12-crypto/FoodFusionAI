import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const acceptDeliveryAssignment = functions.https.onCall(async (data, context) => {
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
        return await db.runTransaction(async (transaction) => {
            const offerDoc = await transaction.get(offerRef);
            if (!offerDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Offer not found");
            }

            const offer = offerDoc.data()!;
            if (offer.driverId !== uid) {
                throw new functions.https.HttpsError("permission-denied", "Offer is not assigned to you");
            }

            if (offer.status !== "PENDING") {
                throw new functions.https.HttpsError("failed-precondition", `Offer status is ${offer.status}`);
            }

            if (offer.expiresAt < Date.now()) {
                transaction.update(offerRef, { status: "EXPIRED" });
                throw new functions.https.HttpsError("failed-precondition", "Offer has expired");
            }

            const orderRef = db.collection("orders").doc(offer.orderId);
            const orderDoc = await transaction.get(orderRef);
            if (!orderDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Order not found");
            }

            const order = orderDoc.data()!;
            if (order.deliveryPartner) {
                transaction.update(offerRef, { status: "EXPIRED" });
                throw new functions.https.HttpsError("failed-precondition", "Order has already been assigned to another driver");
            }

            // Get driver details
            const driverRef = db.collection("drivers").doc(uid);
            const driverDoc = await transaction.get(driverRef);
            if (!driverDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Driver profile not found");
            }
            const driver = driverDoc.data()!;

            // Update offer
            transaction.update(offerRef, { status: "ACCEPTED" });

            // Update order with driver info
            const deliveryPartner = {
                id: uid,
                name: driver.name || "Driver",
                phone: driver.phone || "",
                vehicleType: driver.vehicleType || "BIKE",
                vehicleNumber: driver.vehicleNumber || ""
            };

            const statusHistory = order.statusHistory || [];
            statusHistory.push({
                status: order.orderStatus,
                previousStatus: order.orderStatus,
                timestamp: Date.now(),
                updatedBy: uid,
                message: `Driver ${deliveryPartner.name} assigned to order`
            });

            transaction.update(orderRef, {
                deliveryPartner,
                statusHistory,
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });

            // Set driver availability to BUSY
            transaction.update(driverRef, {
                availability: "BUSY"
            });

            // Write immutable audit log
            const logRef = db.collection("auditLogs").doc();
            transaction.set(logRef, {
                logId: logRef.id,
                actorUid: uid,
                actorRole: "DELIVERY_PARTNER",
                action: "DRIVER_ACCEPTED",
                orderId: offer.orderId,
                driverId: uid,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                before: { deliveryPartner: null },
                after: { deliveryPartner }
            });

            // Send notification to customer
            if (order.userId) {
                const customerDoc = await db.collection("users").doc(order.userId).get();
                const customerToken = customerDoc.data()?.fcmToken;
                if (customerToken) {
                    const customerMsg = {
                        notification: {
                            title: "Driver Assigned",
                            body: `${deliveryPartner.name} is picking up your order!`
                        },
                        data: {
                            orderId: offer.orderId,
                            type: "DRIVER_ASSIGNED",
                            targetId: offer.orderId
                        },
                        token: customerToken
                    };
                    await admin.messaging().send(customerMsg).catch(console.error);
                }
            }

            return { success: true, orderId: offer.orderId, message: "Order successfully assigned" };
        });
    } catch (error: any) {
        console.error("Error in acceptDeliveryAssignment:", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal error");
    }
});
