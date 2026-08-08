package com.foodfusionai.app.data.payment

import com.foodfusionai.app.data.models.order.PaymentMethod

data class PaymentRequest(
    val amount: Double,
    val currency: String = "INR",
    val paymentMethod: PaymentMethod,
    val referenceId: String
)

sealed class PaymentAction {
    data class OpenCheckout(val provider: String, val options: Any) : PaymentAction()
    object NoAction : PaymentAction()
}

sealed class PaymentResult {
    data class Success(val transactionId: String, val referenceId: String, val amount: Double, val signature: String? = null) : PaymentResult()
    data class Failed(val reason: String, val referenceId: String) : PaymentResult()
    data class Cancelled(val reason: String = "User cancelled payment") : PaymentResult()
    data class RequiresAction(val action: PaymentAction) : PaymentResult()
}

interface PaymentGateway {
    /**
     * Initializes the payment SDK and processes the payment.
     */
    suspend fun processPayment(request: PaymentRequest, onProcessing: () -> Unit): PaymentResult
    
    /**
     * Server-side/Gateway-side verification simulation.
     */
    suspend fun verifyPayment(transactionId: String, referenceId: String, signature: String?, expectedAmount: Double): Boolean
}
