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

export const calculateDeliveryEta = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  const { orderId } = data;
  if (!orderId) {
    throw new functions.https.HttpsError("invalid-argument", "Missing orderId parameter");
  }

  const db = admin.firestore();
  const orderSnap = await db.collection("orders").doc(orderId).get();
  if (!orderSnap.exists) {
    throw new functions.https.HttpsError("not-found", `Order ${orderId} not found`);
  }

  const order = orderSnap.data()!;
  
  // Speed profiles in km/minute (Bike speed = 25 km/h -> 25/60 = 0.416 km/min)
  const defaultSpeed = 25 / 60; 

  let restLat = 12.9716;
  let restLng = 77.5946;
  let prepTimeMins = 20;

  // Retrieve restaurant coordinates & preparation metrics
  if (order.restaurantId) {
    const restSnap = await db.collection("restaurants").doc(order.restaurantId).get();
    if (restSnap.exists) {
      const rData = restSnap.data()!;
      if (rData.latitude && rData.longitude) {
        restLat = rData.latitude;
        restLng = rData.longitude;
      }
      prepTimeMins = rData.preparationTime || 20;
    }
  }

  // Retrieve customer coordinates
  let custLat = restLat + 0.015; // default nearby
  let custLng = restLng + 0.015;
  if (order.addressSnapshot && order.addressSnapshot.latitude && order.addressSnapshot.longitude) {
    custLat = order.addressSnapshot.latitude;
    custLng = order.addressSnapshot.longitude;
  } else if (order.deliveryAddress && order.deliveryAddress.latitude && order.deliveryAddress.longitude) {
    custLat = order.deliveryAddress.latitude;
    custLng = order.deliveryAddress.longitude;
  }

  // Retrieve driver coordinates
  let driverLat: number | null = null;
  let driverLng: number | null = null;
  let speed = defaultSpeed;

  if (order.deliveryPartner && order.deliveryPartner.id) {
    const driverSnap = await db.collection("drivers").doc(order.deliveryPartner.id).get();
    if (driverSnap.exists) {
      const dData = driverSnap.data()!;
      if (dData.lastLocation && dData.lastLocation.latitude && dData.lastLocation.longitude) {
        driverLat = dData.lastLocation.latitude;
        driverLng = dData.lastLocation.longitude;
      }
      // Speed profile by vehicle type
      if (dData.vehicleType === "SCOOTER") speed = 30 / 60;
      else if (dData.vehicleType === "CAR") speed = 40 / 60;
      else if (dData.vehicleType === "BICYCLE") speed = 15 / 60;
    }
  }

  const now = new Date().getTime();
  let remainingDistance = 0;
  let estimatedPickupMins = 5; // default dispatch buffer
  let estimatedDeliveryMins = 15;

  // 1. Driver to Restaurant
  if (driverLat !== null && driverLng !== null) {
    const distToRest = calculateDistance(driverLat, driverLng, restLat, restLng);
    estimatedPickupMins = distToRest / speed;
    remainingDistance += distToRest;
  }

  // 2. Restaurant to Customer
  const distToCust = calculateDistance(restLat, restLng, custLat, custLng);
  estimatedDeliveryMins = distToCust / speed;
  remainingDistance += distToCust;

  // 3. Prep remaining logic
  let prepRemainingMins = 0;
  if (order.orderStatus === "CONFIRMED" || order.orderStatus === "PREPARING") {
    const elapsedMins = (now - order.createdAt) / 60000;
    prepRemainingMins = Math.max(0, prepTimeMins - elapsedMins);
  }

  // Calculate timelines
  const pickupTimeMs = now + (Math.max(prepRemainingMins, estimatedPickupMins) * 60000);
  const deliveryTimeMs = pickupTimeMs + (estimatedDeliveryMins * 60000);
  const totalMins = (deliveryTimeMs - now) / 60000;

  // Update order document if we want to synchronize ETA
  await db.collection("orders").doc(orderId).update({
    estimatedDeliveryAt: deliveryTimeMs,
    updatedAt: now
  });

  return {
    success: true,
    estimatedPickupAt: pickupTimeMs,
    estimatedDeliveryAt: deliveryTimeMs,
    remainingDistance: parseFloat(remainingDistance.toFixed(2)),
    estimatedMinutes: Math.round(totalMins)
  };
});
