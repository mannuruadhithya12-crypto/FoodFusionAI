"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.getRazorpayClient = getRazorpayClient;
exports.getRazorpaySecret = getRazorpaySecret;
const razorpay_1 = __importDefault(require("razorpay"));
const params_1 = require("firebase-functions/params");
// Using Firebase parameterized configuration for secrets.
// During local testing/emulation, these are picked from .env files.
const razorpayKeyId = (0, params_1.defineString)("RAZORPAY_KEY_ID");
const razorpayKeySecret = (0, params_1.defineString)("RAZORPAY_KEY_SECRET");
let razorpayInstance = null;
function getRazorpayClient() {
    if (!razorpayInstance) {
        razorpayInstance = new razorpay_1.default({
            key_id: razorpayKeyId.value(),
            key_secret: razorpayKeySecret.value()
        });
    }
    return razorpayInstance;
}
function getRazorpaySecret() {
    return razorpayKeySecret.value();
}
//# sourceMappingURL=razorpayClient.js.map