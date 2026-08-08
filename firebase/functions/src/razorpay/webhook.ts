import * as functions from "firebase-functions";
import * as crypto from "crypto";
import * as admin from "firebase-admin";
import { defineString } from "firebase-functions/params";

const webhookSecret = defineString("RAZORPAY_WEBHOOK_SECRET");

export const razorpayWebhook = functions.https.onRequest(async (req, res) => {
    try {
        const signature = req.headers["x-razorpay-signature"] as string;

        if (!signature) {
            console.error("Missing signature");
            res.status(400).send("Missing signature");
            return;
        }

        // Use raw body for signature verification
        const rawBody = req.rawBody.toString();
        const secret = webhookSecret.value();

        const expectedSignature = crypto
            .createHmac("sha256", secret)
            .update(rawBody)
            .digest("hex");

        if (expectedSignature !== signature) {
            console.error("Invalid signature");
            res.status(400).send("Invalid signature");
            return;
        }

        // Signature verified, process the event
        const event = req.body;
        const eventId = event.id;

        if (!eventId) {
            console.error("Missing event ID");
            res.status(400).send("Missing event ID");
            return;
        }

        const db = admin.firestore();
        const processedEventsRef = db.collection("processed_webhooks").doc(eventId);

        // Run in transaction for idempotency
        await db.runTransaction(async (transaction) => {
            const eventDoc = await transaction.get(processedEventsRef);
            if (eventDoc.exists) {
                // Event already processed
                console.log("Event already processed", eventId);
                return;
            }

            const eventType = event.event;
            const payload = event.payload;

            if (eventType === "payment.captured" || eventType === "order.paid") {
                const payment = payload.payment?.entity;
                const orderId = payment?.order_id;
                
                if (orderId) {
                    // Update order status in Firestore
                    const ordersRef = db.collection("orders").where("paymentReference", "==", orderId);
                    const ordersSnapshot = await transaction.get(ordersRef);

                    if (!ordersSnapshot.empty) {
                        ordersSnapshot.forEach(doc => {
                            transaction.update(doc.ref, {
                                paymentStatus: "SUCCESS",
                                orderStatus: "CONFIRMED",
                                updatedAt: admin.firestore.FieldValue.serverTimestamp()
                            });
                        });
                    }
                }
            } else if (eventType === "payment.failed") {
                 const payment = payload.payment?.entity;
                 const orderId = payment?.order_id;
                 
                 if (orderId) {
                     const ordersRef = db.collection("orders").where("paymentReference", "==", orderId);
                     const ordersSnapshot = await transaction.get(ordersRef);
 
                     if (!ordersSnapshot.empty) {
                         ordersSnapshot.forEach(doc => {
                             transaction.update(doc.ref, {
                                 paymentStatus: "FAILED",
                                 updatedAt: admin.firestore.FieldValue.serverTimestamp()
                             });
                         });
                     }
                 }
            }

            // Mark event as processed
            transaction.set(processedEventsRef, {
                processedAt: admin.firestore.FieldValue.serverTimestamp(),
                type: eventType
            });
        });

        res.status(200).send({ status: "ok" });
    } catch (error) {
        console.error("Webhook processing error:", error);
        res.status(500).send("Internal server error");
    }
});
