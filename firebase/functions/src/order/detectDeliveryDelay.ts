import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const detectDeliveryDelay = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  const db = admin.firestore();
  const now = new Date().getTime();

  // Query all active orders
  const activeStatuses = ["CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY"];
  const ordersSnap = await db.collection("orders")
    .where("orderStatus", "in", activeStatuses)
    .get();

  let delayCount = 0;
  const alertedIds: string[] = [];

  for (const doc of ordersSnap.docs) {
    const orderData = doc.data();
    const orderId = doc.id;

    // Check estimated ready or delivery timestamp
    let estimatedDelivery = orderData.estimatedDeliveryAt;
    
    // If no estimated delivery set, fallback to createdAt + 40 mins
    if (!estimatedDelivery) {
      estimatedDelivery = orderData.createdAt + (40 * 60 * 1000);
    }

    const delayMs = now - estimatedDelivery;
    const delayMins = Math.round(delayMs / 60000);

    if (delayMins > 5) {
      // Order is delayed!
      let severity: "WARNING" | "CRITICAL" = "WARNING";
      let statusTag = "DELAYED";

      if (delayMins > 15) {
        severity = "CRITICAL";
        statusTag = "CRITICAL";
      }

      // Check if we already created an alert for this order to avoid duplicates
      const alertsSnap = await db.collection("operationsAlerts")
        .where("orderId", "==", orderId)
        .where("type", "==", "DELIVERY_DELAY")
        .where("status", "==", "UNRESOLVED")
        .get();

      if (alertsSnap.empty) {
        delayCount++;
        alertedIds.push(orderId);

        // 1. Update order status label
        await db.collection("orders").doc(orderId).update({
          deliveryStatus: statusTag,
          updatedAt: now
        });

        // 2. Create operations alert
        const alertId = db.collection("operationsAlerts").doc().id;
        await db.collection("operationsAlerts").doc(alertId).set({
          alertId,
          type: "DELIVERY_DELAY",
          severity,
          orderId,
          message: `Order #${orderId.slice(0, 8).toUpperCase()} is delayed by +${delayMins} minutes.`,
          status: "UNRESOLVED",
          createdAt: admin.firestore.FieldValue.serverTimestamp()
        });

        // 3. Notify customer
        if (orderData.userId) {
          const userSnap = await db.collection("users").doc(orderData.userId).get();
          const fcmToken = userSnap.exists ? userSnap.data()?.fcmToken : null;
          if (fcmToken) {
            try {
              const payload = {
                notification: {
                  title: "Order update",
                  body: "Your food is taking a little longer than expected. Our support team is expediting it!"
                },
                data: {
                  type: "ORDER_DELAYED",
                  orderId,
                  delayMins: delayMins.toString()
                },
                token: fcmToken
              };
              await admin.messaging().send(payload);
            } catch (err) {
              console.warn("FCM delay notification failed for user:", orderData.userId, err);
            }
          }
        }

        // 4. Log in Audit timeline
        await db.collection("auditLogs").add({
          action: "ORDER_DELAYED",
          actorUid: "SYSTEM",
          actorRole: "SYSTEM",
          orderId,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          delayMinutes: delayMins,
          severity
        });
      }
    }
  }

  return {
    success: true,
    scannedCount: ordersSnap.size,
    flaggedDelayCount: delayCount,
    alertedOrders: alertedIds
  };
});
