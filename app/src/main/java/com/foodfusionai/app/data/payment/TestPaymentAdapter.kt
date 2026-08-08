package com.foodfusionai.app.data.payment

import kotlinx.coroutines.delay
import java.util.UUID

/**
 * A test implementation of the PaymentGateway to simulate real payment processing safely.
 * Magic amounts can trigger different scenarios:
 * amount = 9999.0 -> simulates failure (Insufficient Funds)
 * amount = 8888.0 -> simulates cancellation
 * all other valid amounts > 0 -> success
 */
class TestPaymentAdapter : PaymentGateway {

    private val successfulTransactions = mutableMapOf<String, Double>()

    override suspend fun processPayment(
        request: PaymentRequest,
        onProcessing: () -> Unit
    ): PaymentResult {
        // PENDING -> PROCESSING
        onProcessing()
        
        // Simulate network delay for UI state testing
        delay(2000)
        
        if (request.amount <= 0.0) {
            return PaymentResult.Failed("Invalid amount: ₹${request.amount}", request.referenceId)
        }

        if (request.amount == 9999.0) {
            return PaymentResult.Failed("Insufficient Funds or Bank Error", request.referenceId)
        }

        if (request.amount == 8888.0) {
            return PaymentResult.Cancelled("User aborted the payment flow")
        }

        // Simulate successful transaction
        val transactionId = "TXN_${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
        successfulTransactions[transactionId] = request.amount
        
        return PaymentResult.Success(
            transactionId = transactionId,
            referenceId = request.referenceId,
            amount = request.amount
        )
    }

    override suspend fun verifyPayment(
        transactionId: String,
        referenceId: String,
        expectedAmount: Double
    ): Boolean {
        // Simulate verification network delay
        delay(1000)
        
        val actualAmount = successfulTransactions[transactionId] ?: return false
        
        // Ensure precise matching
        return Math.abs(actualAmount - expectedAmount) < 0.01
    }
}
