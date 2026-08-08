package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.order.PaymentMethod
import com.foodfusionai.app.data.payment.PaymentRequest
import com.foodfusionai.app.data.payment.PaymentResult
import com.foodfusionai.app.data.payment.TestPaymentAdapter
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PaymentRepositoryTest {

    private lateinit var paymentRepository: PaymentRepository
    private lateinit var testAdapter: TestPaymentAdapter

    @Before
    fun setup() {
        testAdapter = TestPaymentAdapter()
        paymentRepository = PaymentRepositoryImpl(testAdapter)
    }

    @Test
    fun `processPayment with valid amount emits Success`() = runBlocking {
        val request = PaymentRequest(
            amount = 500.0,
            paymentMethod = PaymentMethod.UPI,
            referenceId = "REF_123"
        )
        val results = paymentRepository.initiatePayment(request).toList()
        
        assertTrue(results[0] is Resource.Loading)
        assertTrue(results[1] is Resource.Success)
        val data = (results[1] as Resource.Success).data
        assertTrue(data is PaymentResult.Success)
        assertEquals(500.0, (data as PaymentResult.Success).amount, 0.01)
    }

    @Test
    fun `processPayment with 9999 emits Failed`() = runBlocking {
        val request = PaymentRequest(
            amount = 9999.0, // Magic amount for Insufficient Funds
            paymentMethod = PaymentMethod.UPI,
            referenceId = "REF_123"
        )
        val results = paymentRepository.initiatePayment(request).toList()
        
        assertTrue(results[1] is Resource.Success)
        val data = (results[1] as Resource.Success).data
        assertTrue(data is PaymentResult.Failed)
    }

    @Test
    fun `processPayment with 8888 emits Cancelled`() = runBlocking {
        val request = PaymentRequest(
            amount = 8888.0, // Magic amount for Cancelled
            paymentMethod = PaymentMethod.UPI,
            referenceId = "REF_123"
        )
        val results = paymentRepository.initiatePayment(request).toList()
        
        assertTrue(results[1] is Resource.Success)
        val data = (results[1] as Resource.Success).data
        assertTrue(data is PaymentResult.Cancelled)
    }

    @Test
    fun `processPayment with negative amount emits Failed`() = runBlocking {
        val request = PaymentRequest(
            amount = -50.0,
            paymentMethod = PaymentMethod.UPI,
            referenceId = "REF_123"
        )
        val results = paymentRepository.initiatePayment(request).toList()
        
        assertTrue(results[1] is Resource.Success)
        val data = (results[1] as Resource.Success).data
        assertTrue(data is PaymentResult.Failed)
    }
    
    @Test
    fun `verifyPayment returns true for exact amount match`() = runBlocking {
        val request = PaymentRequest(
            amount = 350.0,
            paymentMethod = PaymentMethod.UPI,
            referenceId = "REF_123"
        )
        val initRes = paymentRepository.initiatePayment(request).toList().last() as Resource.Success
        val successData = initRes.data as PaymentResult.Success
        
        val verifyRes = paymentRepository.verifyPayment(successData.transactionId, successData.referenceId, 350.0)
        assertTrue(verifyRes is Resource.Success && verifyRes.data == true)
    }
    
    @Test
    fun `verifyPayment returns false for amount mismatch`() = runBlocking {
        val request = PaymentRequest(
            amount = 350.0,
            paymentMethod = PaymentMethod.UPI,
            referenceId = "REF_123"
        )
        val initRes = paymentRepository.initiatePayment(request).toList().last() as Resource.Success
        val successData = initRes.data as PaymentResult.Success
        
        val verifyRes = paymentRepository.verifyPayment(successData.transactionId, successData.referenceId, 300.0)
        assertTrue(verifyRes is Resource.Success && verifyRes.data == false)
    }
}
