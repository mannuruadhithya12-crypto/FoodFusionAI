import Razorpay from "razorpay";
import { defineString } from "firebase-functions/params";

// Using Firebase parameterized configuration for secrets.
// During local testing/emulation, these are picked from .env files.
const razorpayKeyId = defineString("RAZORPAY_KEY_ID");
const razorpayKeySecret = defineString("RAZORPAY_KEY_SECRET");

let razorpayInstance: Razorpay | null = null;

export function getRazorpayClient(): Razorpay {
    if (!razorpayInstance) {
        razorpayInstance = new Razorpay({
            key_id: razorpayKeyId.value(),
            key_secret: razorpayKeySecret.value()
        });
    }
    return razorpayInstance;
}

export function getRazorpaySecret(): string {
    return razorpayKeySecret.value();
}
