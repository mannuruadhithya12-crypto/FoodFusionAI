package com.foodfusionai.app.data.payment

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class RazorpayPaymentAdapter(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : PaymentGateway {

    override suspend fun processPayment(
        request: PaymentRequest,
        onProcessing: () -> Unit
    ): PaymentResult {
        onProcessing()

        return try {
            val data = hashMapOf<String, Any>(
                "amount" to request.amount,
                "currency" to request.currency,
                "checkoutReference" to request.referenceId
            )
            
            request.couponId?.let { data["couponId"] = it }
            request.cartTotal?.let { data["cartTotal"] = it }

            // Call the Firebase Function
            val result = functions
                .getHttpsCallable("createRazorpayOrder")
                .call(data)
                .await()
            
            val responseData = result.data as? Map<String, Any>
            val orderId = responseData?.get("orderId") as? String
                ?: return PaymentResult.Failed("Failed to get order ID from backend", request.referenceId)
            
            val amountPaise = responseData["amount"] as? Int ?: (request.amount * 100).toInt()

            // Prepare Razorpay options
            val options = JSONObject()
            options.put("name", "FoodFusion AI")
            options.put("description", "Food Order ${request.referenceId}")
            options.put("image", "https://s3.amazonaws.com/rzp-mobile/images/rzp.png") // Placeholder logo
            options.put("order_id", orderId)
            options.put("theme.color", "#E44D26") // Theme color matching app if possible
            options.put("currency", "INR")
            options.put("amount", amountPaise)
            
            val prefill = JSONObject()
            // In a real app we'd pass user details here
            prefill.put("email", "test@foodfusionai.com")
            prefill.put("contact", "9999999999")
            options.put("prefill", prefill)

            PaymentResult.RequiresAction(PaymentAction.OpenCheckout("razorpay", options))
            
        } catch (e: Exception) {
            PaymentResult.Failed("Payment initialization failed: ${e.message}", request.referenceId)
        }
    }

    override suspend fun verifyPayment(
        transactionId: String,
        referenceId: String,
        signature: String?,
        expectedAmount: Double
    ): Boolean {
        if (signature == null) return false

        return try {
            val data = hashMapOf(
                "paymentId" to transactionId,
                "signature" to signature,
                "checkoutReference" to referenceId
            )

            val result = functions
                .getHttpsCallable("verifyRazorpayPayment")
                .call(data)
                .await()

            val responseData = result.data as? Map<String, Any>
            val verified = responseData?.get("verified") as? Boolean ?: false
            
            verified
        } catch (e: Exception) {
            false
        }
    }
}
