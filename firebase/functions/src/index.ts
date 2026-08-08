import * as admin from "firebase-admin";

admin.initializeApp();

export { createRazorpayOrder } from "./razorpay/createOrder";
export { verifyRazorpayPayment } from "./razorpay/verifyPayment";
export { razorpayWebhook } from "./razorpay/webhook";
export { cancelOrder } from "./order/cancelOrder";
export { updateOrderStatus } from "./order/updateOrderStatus";
export { onOrderStatusUpdated } from "./order/fcmNotification";
export { setDefaultAddress } from "./user/setDefaultAddress";
export { deleteUserAccount } from "./user/deleteUserAccount";
