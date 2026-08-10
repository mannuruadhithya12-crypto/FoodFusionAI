"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.removeRestaurantStaff = exports.inviteRestaurantStaff = exports.partnerUpdateOrderStatus = exports.partnerRejectOrder = exports.partnerAcceptOrder = exports.reassignDriver = exports.detectDeliveryDelay = exports.calculateDeliveryEta = exports.dispatchReadyOrder = exports.suspendDriver = exports.approveDriver = exports.reportDeliveryIssue = exports.verifyDeliveryOtp = exports.updateDeliveryStatus = exports.declineDeliveryAssignment = exports.acceptDeliveryAssignment = exports.assignDriverToOrder = exports.bootstrapAdmin = exports.interactReview = exports.deleteReview = exports.editReview = exports.createReview = exports.deleteUserAccount = exports.setDefaultAddress = exports.validateCoupon = exports.onOrderStatusUpdated = exports.updateOrderStatus = exports.cancelOrder = exports.razorpayWebhook = exports.verifyRazorpayPayment = exports.createRazorpayOrder = exports.getRecommendations = void 0;
const admin = __importStar(require("firebase-admin"));
admin.initializeApp();
var getRecommendations_1 = require("./user/getRecommendations");
Object.defineProperty(exports, "getRecommendations", { enumerable: true, get: function () { return getRecommendations_1.getRecommendations; } });
var createOrder_1 = require("./razorpay/createOrder");
Object.defineProperty(exports, "createRazorpayOrder", { enumerable: true, get: function () { return createOrder_1.createRazorpayOrder; } });
var verifyPayment_1 = require("./razorpay/verifyPayment");
Object.defineProperty(exports, "verifyRazorpayPayment", { enumerable: true, get: function () { return verifyPayment_1.verifyRazorpayPayment; } });
var webhook_1 = require("./razorpay/webhook");
Object.defineProperty(exports, "razorpayWebhook", { enumerable: true, get: function () { return webhook_1.razorpayWebhook; } });
var cancelOrder_1 = require("./order/cancelOrder");
Object.defineProperty(exports, "cancelOrder", { enumerable: true, get: function () { return cancelOrder_1.cancelOrder; } });
var updateOrderStatus_1 = require("./order/updateOrderStatus");
Object.defineProperty(exports, "updateOrderStatus", { enumerable: true, get: function () { return updateOrderStatus_1.updateOrderStatus; } });
var fcmNotification_1 = require("./order/fcmNotification");
Object.defineProperty(exports, "onOrderStatusUpdated", { enumerable: true, get: function () { return fcmNotification_1.onOrderStatusUpdated; } });
var validateCoupon_1 = require("./order/validateCoupon");
Object.defineProperty(exports, "validateCoupon", { enumerable: true, get: function () { return validateCoupon_1.validateCoupon; } });
var setDefaultAddress_1 = require("./user/setDefaultAddress");
Object.defineProperty(exports, "setDefaultAddress", { enumerable: true, get: function () { return setDefaultAddress_1.setDefaultAddress; } });
var deleteUserAccount_1 = require("./user/deleteUserAccount");
Object.defineProperty(exports, "deleteUserAccount", { enumerable: true, get: function () { return deleteUserAccount_1.deleteUserAccount; } });
var createReview_1 = require("./reviews/createReview");
Object.defineProperty(exports, "createReview", { enumerable: true, get: function () { return createReview_1.createReview; } });
var editReview_1 = require("./reviews/editReview");
Object.defineProperty(exports, "editReview", { enumerable: true, get: function () { return editReview_1.editReview; } });
var deleteReview_1 = require("./reviews/deleteReview");
Object.defineProperty(exports, "deleteReview", { enumerable: true, get: function () { return deleteReview_1.deleteReview; } });
var interactReview_1 = require("./reviews/interactReview");
Object.defineProperty(exports, "interactReview", { enumerable: true, get: function () { return interactReview_1.interactReview; } });
var bootstrapAdmin_1 = require("./admin/bootstrapAdmin");
Object.defineProperty(exports, "bootstrapAdmin", { enumerable: true, get: function () { return bootstrapAdmin_1.bootstrapAdmin; } });
// Driver Platform Functions
var assignDriverToOrder_1 = require("./order/assignDriverToOrder");
Object.defineProperty(exports, "assignDriverToOrder", { enumerable: true, get: function () { return assignDriverToOrder_1.assignDriverToOrder; } });
var acceptDeliveryAssignment_1 = require("./order/acceptDeliveryAssignment");
Object.defineProperty(exports, "acceptDeliveryAssignment", { enumerable: true, get: function () { return acceptDeliveryAssignment_1.acceptDeliveryAssignment; } });
var declineDeliveryAssignment_1 = require("./order/declineDeliveryAssignment");
Object.defineProperty(exports, "declineDeliveryAssignment", { enumerable: true, get: function () { return declineDeliveryAssignment_1.declineDeliveryAssignment; } });
var updateDeliveryStatus_1 = require("./order/updateDeliveryStatus");
Object.defineProperty(exports, "updateDeliveryStatus", { enumerable: true, get: function () { return updateDeliveryStatus_1.updateDeliveryStatus; } });
var verifyDeliveryOtp_1 = require("./order/verifyDeliveryOtp");
Object.defineProperty(exports, "verifyDeliveryOtp", { enumerable: true, get: function () { return verifyDeliveryOtp_1.verifyDeliveryOtp; } });
var reportDeliveryIssue_1 = require("./order/reportDeliveryIssue");
Object.defineProperty(exports, "reportDeliveryIssue", { enumerable: true, get: function () { return reportDeliveryIssue_1.reportDeliveryIssue; } });
var approveDriver_1 = require("./admin/approveDriver");
Object.defineProperty(exports, "approveDriver", { enumerable: true, get: function () { return approveDriver_1.approveDriver; } });
Object.defineProperty(exports, "suspendDriver", { enumerable: true, get: function () { return approveDriver_1.suspendDriver; } });
var dispatchReadyOrder_1 = require("./order/dispatchReadyOrder");
Object.defineProperty(exports, "dispatchReadyOrder", { enumerable: true, get: function () { return dispatchReadyOrder_1.dispatchReadyOrder; } });
var calculateDeliveryEta_1 = require("./order/calculateDeliveryEta");
Object.defineProperty(exports, "calculateDeliveryEta", { enumerable: true, get: function () { return calculateDeliveryEta_1.calculateDeliveryEta; } });
var detectDeliveryDelay_1 = require("./order/detectDeliveryDelay");
Object.defineProperty(exports, "detectDeliveryDelay", { enumerable: true, get: function () { return detectDeliveryDelay_1.detectDeliveryDelay; } });
var reassignDriver_1 = require("./order/reassignDriver");
Object.defineProperty(exports, "reassignDriver", { enumerable: true, get: function () { return reassignDriver_1.reassignDriver; } });
var partnerFunctions_1 = require("./partner/partnerFunctions");
Object.defineProperty(exports, "partnerAcceptOrder", { enumerable: true, get: function () { return partnerFunctions_1.partnerAcceptOrder; } });
Object.defineProperty(exports, "partnerRejectOrder", { enumerable: true, get: function () { return partnerFunctions_1.partnerRejectOrder; } });
Object.defineProperty(exports, "partnerUpdateOrderStatus", { enumerable: true, get: function () { return partnerFunctions_1.partnerUpdateOrderStatus; } });
Object.defineProperty(exports, "inviteRestaurantStaff", { enumerable: true, get: function () { return partnerFunctions_1.inviteRestaurantStaff; } });
Object.defineProperty(exports, "removeRestaurantStaff", { enumerable: true, get: function () { return partnerFunctions_1.removeRestaurantStaff; } });
//# sourceMappingURL=index.js.map