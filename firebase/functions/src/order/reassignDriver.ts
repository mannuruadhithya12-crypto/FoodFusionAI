import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const reassignDriver = functions.https.onCall(async (data, context) => {
  // 1. Authenticate and Admin check
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  // Retrieve user custom claims or check database
  const db = admin.firestore();
  const callerUid = context.auth.uid;
  const adminSnap = await db.collection("adminUsers").doc(callerUid).get();
  const isCallerAdmin = context.auth.token.admin === true || adminSnap.exists;

  if (!isCallerAdmin) {
    throw new functions.https.HttpsError("permission-denied", "Only administrators can reassign delivery partners");
  }

  const { orderId, reason } = data;
  if (!orderId || !reason) {
    throw new functions.https.HttpsError("invalid-argument", "Missing orderId or reason parameters");
  }

  // 2. Fetch Order
  const orderRef = db.collection("orders").doc(orderId);
  const orderSnap = await orderRef.get();
  if (!orderSnap.exists) {
    throw new functions.https.HttpsError("not-found", `Order ${orderId} not found`);
  }

  const order = orderSnap.data()!;
  
  // Validate safety restriction: Cannot reassign if completed/cancelled
  if (order.orderStatus === "DELIVERED" || order.orderStatus === "CANCELLED") {
    throw new functions.https.HttpsError("failed-precondition", "Cannot reassign driver on completed or cancelled order");
  }

  const oldDriver = order.deliveryPartner;
  if (!oldDriver) {
    throw new functions.https.HttpsError("failed-precondition", "No driver is assigned to this order to unassign");
  }

  // 3. Clear driver details and reset order status
  const now = new Date().getTime();
  await orderRef.update({
    deliveryPartner: admin.firestore.FieldValue.delete(),
    deliveryStatus: "UNASSIGNED",
    orderStatus: "READY_FOR_PICKUP",
    updatedAt: now
  });

  // 4. Update the unassigned driver profile
  const driverRef = db.collection("drivers").doc(oldDriver.id);
  await driverRef.update({
    availability: "ONLINE",
    activeOrderId: admin.firestore.FieldValue.delete(),
    updatedAt: now
  });

  // 5. Log the action to auditLogs
  await db.collection("auditLogs").add({
    action: "DRIVER_REASSIGNED",
    actorUid: callerUid,
    actorRole: "SUPER_ADMIN",
    orderId,
    driverId: oldDriver.id,
    reason,
    timestamp: admin.firestore.FieldValue.serverTimestamp()
  });

  return {
    success: true,
    message: "Driver unassigned successfully. Order is back in dispatch pool."
  };
});
