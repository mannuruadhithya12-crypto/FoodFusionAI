package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.payment.PaymentGateway
import com.foodfusionai.app.data.payment.PaymentRequest
import com.foodfusionai.app.data.payment.PaymentResult
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PaymentRepositoryImpl(
    private val paymentGateway: PaymentGateway
) : PaymentRepository {

    override fun initiatePayment(request: PaymentRequest): Flow<Resource<PaymentResult>> = flow {
        emit(Resource.Loading)
        try {
            val result = paymentGateway.processPayment(request) {
                // Not emitting another loading to avoid confusing the flow,
                // UI already handles Loading state.
            }
            emit(Resource.Success(result))
        } catch (e: Exception) {
            emit(Resource.Error("Payment initialization failed: ${e.message}"))
        }
    }

    override suspend fun verifyPayment(
        transactionId: String,
        referenceId: String,
        expectedAmount: Double
    ): Resource<Boolean> {
        return try {
            val isVerified = paymentGateway.verifyPayment(transactionId, referenceId, expectedAmount)
            Resource.Success(isVerified)
        } catch (e: Exception) {
            Resource.Error("Payment verification failed: ${e.message}")
        }
    }
}
