import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const assignDriverToOrder = functions.https.onCall(async (data, context) => {
    // Authenticate context
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Auth required");
    }

    const { orderId } = data;
    if (!orderId) {
        throw new functions.https.HttpsError("invalid-argument", "Order ID required");
    }

    const db = admin.firestore();
    const orderRef = db.collection("orders").doc(orderId);

    try {
        return await db.runTransaction(async (transaction) => {
            const orderDoc = await transaction.get(orderRef);
            if (!orderDoc.exists) {
                throw new functions.https.HttpsError("not-found", "Order not found");
            }

            const order = orderDoc.data();
            if (order?.orderStatus !== "READY_FOR_PICKUP") {
                throw new functions.https.HttpsError("failed-precondition", "Order must be READY_FOR_PICKUP to assign a driver");
            }

            if (order?.deliveryPartner) {
                throw new functions.https.HttpsError("failed-precondition", "Driver already assigned to this order");
            }

            // Find all online, approved drivers who are not busy
            const driversSnap = await db.collection("drivers")
                .where("status", "==", "APPROVED")
                .where("availability", "==", "ONLINE")
                .get();

            if (driversSnap.empty) {
                return { success: false, reason: "NO_DRIVERS_AVAILABLE", message: "No online drivers available right now" };
            }

            // Pick the first available driver (or implement geo-distance if needed)
            const driverDoc = driversSnap.docs[0];
            const driverId = driverDoc.id;
            const driverData = driverDoc.data();

            // Create assignment offer with 30s timeout
            const offerRef = db.collection("driverOffers").doc();
            const offerId = offerRef.id;
            const offerData = {
                offerId,
                orderId,
                driverId,
                status: "PENDING",
                createdAt: Date.now(),
                expiresAt: Date.now() + 30000, // 30 seconds expiry
                restaurantName: order.restaurantName || "Food Fusion Partner",
                restaurantAddress: order.addressSnapshot?.street || "Restaurant Address",
                deliveryArea: order.addressSnapshot?.city || "Customer Location"
            };

            transaction.set(offerRef, offerData);

            // Send notification to the driver via FCM
            if (driverData.fcmToken) {
                const message = {
                    notification: {
                        title: "New Delivery Request",
                        body: `Accept delivery for ${offerData.restaurantName}`
                    },
                    data: {
                        offerId,
                        orderId,
                        type: "NEW_DELIVERY_REQUEST",
                        targetId: offerId
                    },
                    token: driverData.fcmToken
                };
                try {
                    await admin.messaging().send(message);
                } catch (err) {
                    console.error("FCM failure to driver:", err);
                }
            }

            return { success: true, offerId, driverId, message: "Delivery offered to driver successfully" };
        });
    } catch (error: any) {
        console.error("Error in assignDriverToOrder:", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", error.message || "Internal error");
    }
});
