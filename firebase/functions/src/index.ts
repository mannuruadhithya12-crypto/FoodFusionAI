import * as admin from "firebase-admin";

admin.initializeApp();

export { getRecommendations } from "./user/getRecommendations";
export { createRazorpayOrder } from "./razorpay/createOrder";
export { verifyRazorpayPayment } from "./razorpay/verifyPayment";
export { razorpayWebhook } from "./razorpay/webhook";
export { cancelOrder } from "./order/cancelOrder";
export { updateOrderStatus } from "./order/updateOrderStatus";
export { onOrderStatusUpdated } from "./order/fcmNotification";
export { validateCoupon } from "./order/validateCoupon";
export { setDefaultAddress } from "./user/setDefaultAddress";
export { deleteUserAccount } from "./user/deleteUserAccount";

export { createReview } from "./reviews/createReview";
export { editReview } from "./reviews/editReview";
export { deleteReview } from "./reviews/deleteReview";
export { interactReview } from "./reviews/interactReview";

export { bootstrapAdmin } from "./admin/bootstrapAdmin";

// Driver Platform Functions
export { assignDriverToOrder } from "./order/assignDriverToOrder";
export { acceptDeliveryAssignment } from "./order/acceptDeliveryAssignment";
export { declineDeliveryAssignment } from "./order/declineDeliveryAssignment";
export { updateDeliveryStatus } from "./order/updateDeliveryStatus";
export { verifyDeliveryOtp } from "./order/verifyDeliveryOtp";
export { reportDeliveryIssue } from "./order/reportDeliveryIssue";
export { approveDriver, suspendDriver } from "./admin/approveDriver";
export { dispatchReadyOrder } from "./order/dispatchReadyOrder";
export { calculateDeliveryEta } from "./order/calculateDeliveryEta";
export { detectDeliveryDelay } from "./order/detectDeliveryDelay";
export { reassignDriver } from "./order/reassignDriver";


export {
    partnerAcceptOrder,
    partnerRejectOrder,
    partnerUpdateOrderStatus,
    inviteRestaurantStaff,
    removeRestaurantStaff
} from "./partner/partnerFunctions";
