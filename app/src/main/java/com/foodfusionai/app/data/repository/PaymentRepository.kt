package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.payment.PaymentRequest
import com.foodfusionai.app.data.payment.PaymentResult
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun initiatePayment(request: PaymentRequest): Flow<Resource<PaymentResult>>
    
    suspend fun verifyPayment(
        transactionId: String,
        referenceId: String,
        expectedAmount: Double
    ): Resource<Boolean>
}
