import * as admin from "firebase-admin";

admin.initializeApp();

// ── Phase 1–14: existing functions (unchanged) ─────────────────────────────
export { createRazorpayOrder }    from "./razorpay/createOrder";
export { verifyRazorpayPayment }  from "./razorpay/verifyPayment";
export { razorpayWebhook }        from "./razorpay/webhook";
export { cancelOrder }            from "./order/cancelOrder";
export { updateOrderStatus }      from "./order/updateOrderStatus";
export { onOrderStatusUpdated }   from "./order/fcmNotification";
export { setDefaultAddress }      from "./user/setDefaultAddress";
export { deleteUserAccount }      from "./user/deleteUserAccount";

// ── Phase 16: Real Maps, Geolocation & Live Delivery Platform ─────────────
export { updateDriverLocation }      from "./location/updateDriverLocation";
export { deactivateDriverLocation }  from "./location/deactivateDriverLocation";
export { validateDeliveryLocation }  from "./location/validateDeliveryLocation";
export { getNearbyRestaurants }      from "./location/getNearbyRestaurants";
export { calculateLiveEta }          from "./location/calculateLiveEta";
export { flagSuspiciousLocation }    from "./location/flagSuspiciousLocation";
