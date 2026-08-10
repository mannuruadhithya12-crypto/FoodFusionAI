import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371; // Earth's radius in km
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

export const dispatchReadyOrder = functions.https.onCall(async (data, context) => {
  // 1. Authenticate check
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  const { orderId } = data;
  if (!orderId) {
    throw new functions.https.HttpsError("invalid-argument", "Missing orderId parameter");
  }

  const db = admin.firestore();

  // 2. Fetch configurations with auto-populating default config if missing
  const configRef = db.collection("config").doc("dispatchSettings");
  let configSnap = await configRef.get();
  if (!configSnap.exists) {
    const defaultConfig = {
      distanceWeight: 0.4,
      reliabilityWeight: 0.2,
      workloadWeight: 0.2,
      etaWeight: 0.2,
      maxDriverDistanceKm: 5.0,
      enableBatching: false,
      timerExpiryMs: 30000
    };
    await configRef.set(defaultConfig);
    configSnap = await configRef.get();
  }
  const config = configSnap.data()!;

  // 3. Load order detail
  const orderRef = db.collection("orders").doc(orderId);
  const orderSnap = await orderRef.get();
  if (!orderSnap.exists) {
    throw new functions.https.HttpsError("not-found", `Order ${orderId} not found`);
  }

  const order = orderSnap.data()!;
  if (order.orderStatus !== "READY_FOR_PICKUP") {
    throw new functions.https.HttpsError("failed-precondition", `Order ${orderId} must be READY_FOR_PICKUP to dispatch`);
  }

  if (order.deliveryPartner) {
    throw new functions.https.HttpsError("already-exists", `Order ${orderId} already has an assigned driver`);
  }

  // Get restaurant coordinates
  let restLat = 12.9716; // default Bangalore center
  let restLng = 77.5946;
  
  if (order.restaurantId) {
    const restSnap = await db.collection("restaurants").doc(order.restaurantId).get();
    if (restSnap.exists) {
      const restData = restSnap.data()!;
      if (restData.latitude && restData.longitude) {
        restLat = restData.latitude;
        restLng = restData.longitude;
      }
    }
  }

  // 4. Fetch all Online, Approved drivers
  const driversSnap = await db.collection("drivers")
    .where("status", "==", "APPROVED")
    .where("availability", "==", "ONLINE")
    .get();

  const candidates: Array<{ driverId: string; data: any; score: number; distance: number }> = [];

  for (const doc of driversSnap.docs) {
    const driverData = doc.data();
    const dLoc = driverData.lastLocation;

    if (!dLoc || !dLoc.latitude || !dLoc.longitude) {
      continue; // Skip drivers with no location
    }

    const distance = calculateDistance(dLoc.latitude, dLoc.longitude, restLat, restLng);
    if (distance > config.maxDriverDistanceKm) {
      continue; // Skip out of range drivers
    }

    // Calculate score
    const distanceScore = Math.max(0, 100 - (distance * 15));
    const reliabilityScore = driverData.reliability || 90;
    const workloadScore = (driverData.activeDeliveriesCount || 0) === 0 ? 100 : 50;
    
    // ETA travel score: Bike goes at ~25 km/h -> 2.4 mins per km
    const estMinutes = distance * 2.4;
    const etaScore = Math.max(0, 100 - (estMinutes * 5));

    const totalScore = (distanceScore * config.distanceWeight) +
                       (reliabilityScore * config.reliabilityWeight) +
                       (workloadScore * config.workloadWeight) +
                       (etaScore * config.etaWeight);

    candidates.push({
      driverId: doc.id,
      data: driverData,
      score: totalScore,
      distance: distance
    });
  }

  // 5. Sort candidate drivers by score desc
  candidates.sort((a, b) => b.score - a.score);

  if (candidates.length === 0) {
    // Flag order as at risk
    await orderRef.update({
      deliveryStatus: "AT_RISK",
      updatedAt: new Date().getTime()
    });

    // Create operations alert
    const alertId = db.collection("operationsAlerts").doc().id;
    await db.collection("operationsAlerts").doc(alertId).set({
      alertId,
      type: "UNASSIGNED_ORDER",
      severity: "CRITICAL",
      orderId,
      message: `No eligible drivers found in range (${config.maxDriverDistanceKm} km) for order ${orderId}`,
      status: "UNRESOLVED",
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });

    return {
      success: false,
      message: "No available drivers in range"
    };
  }

  // 6. Offer order to highest scoring candidate driver
  const topCandidate = candidates[0];
  const offerId = db.collection("driverOffers").doc().id;
  const expiresAt = new Date().getTime() + config.timerExpiryMs;

  await db.collection("driverOffers").doc(offerId).set({
    offerId,
    orderId,
    driverId: topCandidate.driverId,
    score: topCandidate.score,
    distance: topCandidate.distance,
    expiresAt,
    status: "PENDING",
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });

  // Notify driver via FCM
  if (topCandidate.data.fcmToken) {
    try {
      const payload = {
        notification: {
          title: "New Delivery Offer!",
          body: `Earn ₹50 delivering order from ${order.restaurantName || "FoodFusion Restaurant"}`
        },
        data: {
          click_action: "FLUTTER_NOTIFICATION_CLICK",
          type: "DELIVERY_OFFER",
          offerId,
          orderId,
          expiresAt: expiresAt.toString()
        },
        token: topCandidate.data.fcmToken
      };
      await admin.messaging().send(payload);
    } catch (err) {
      console.warn("FCM notify failed for driver:", topCandidate.driverId, err);
    }
  }

  // Log in Audit timeline
  await db.collection("auditLogs").add({
    action: "DISPATCH_CREATED",
    actorUid: context.auth.uid,
    actorRole: "SYSTEM",
    orderId,
    driverId: topCandidate.driverId,
    timestamp: admin.firestore.FieldValue.serverTimestamp(),
    score: topCandidate.score
  });

  return {
    success: true,
    offerId,
    driverId: topCandidate.driverId,
    score: topCandidate.score
  };
});
