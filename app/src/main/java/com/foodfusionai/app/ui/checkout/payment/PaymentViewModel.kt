package com.foodfusionai.app.ui.checkout.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.models.order.Order
import com.foodfusionai.app.data.payment.PaymentRequest
import com.foodfusionai.app.data.payment.PaymentResult
import com.foodfusionai.app.data.repository.CartRepository
import com.foodfusionai.app.data.repository.OrderRepository
import com.foodfusionai.app.data.repository.PaymentRepository
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaymentUiState(
    val isProcessing: Boolean = false,
    val paymentResult: PaymentResult? = null,
    val orderCreated: Order? = null,
    val error: String? = null,
    val isCartCleared: Boolean = false
)

class PaymentViewModel(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val cartRepository: CartRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var pendingOrderSnapshot: Order? = null

    /**
     * Initializes the payment flow and sets up the atomic process of order creation.
     */
    fun startPaymentFlow(request: PaymentRequest, orderSnapshot: Order) {
        pendingOrderSnapshot = orderSnapshot
        coroutineScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            
            // 1. Create the order first (Server sets it to PENDING)
            orderRepository.createOrder(orderSnapshot).collect { orderRes ->
                when (orderRes) {
                    is Resource.Success -> {
                        val createdOrder = orderRes.data!!
                        val newRequest = request.copy(checkoutReference = createdOrder.orderId)
                        
                        // 2. Initiate Payment with the DB orderId as reference
                        paymentRepository.initiatePayment(newRequest).collect { res ->
                            when (res) {
                                is Resource.Loading -> { /* Keep processing */ }
                                is Resource.Success -> {
                                    val result = res.data
                                    if (result is PaymentResult.RequiresAction) {
                                        _uiState.update { it.copy(isProcessing = false, paymentResult = result) }
                                    } else if (result is PaymentResult.Success) {
                                        verifyPayment(result.transactionId, result.referenceId, result.signature, result.amount, createdOrder)
                                    } else {
                                        _uiState.update { it.copy(isProcessing = false, paymentResult = result) }
                                    }
                                }
                                is Resource.Error -> {
                                    _uiState.update { it.copy(isProcessing = false, error = res.message) }
                                }
                                is Resource.Empty -> {
                                    _uiState.update { it.copy(isProcessing = false) }
                                }
                            }
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isProcessing = false, error = "Order creation failed: ${orderRes.message}") }
                    }
                    is Resource.Loading -> {}
                    is Resource.Empty -> {}
                }
            }
        }
    }

    /**
     * Called by the fragment after receiving Razorpay callback.
     */
    fun submitRazorpayResult(callbackResult: RazorpayCallbackResult, amount: Double, referenceId: String) {
        when (callbackResult) {
            is RazorpayCallbackResult.Success -> {
                _uiState.update { it.copy(isProcessing = true) }
                coroutineScope.launch {
                    verifyPayment(
                        transactionId = callbackResult.paymentId,
                        referenceId = referenceId, // This is our DB orderId now
                        signature = callbackResult.signature,
                        amount = amount,
                        order = pendingOrderSnapshot
                    )
                }
            }
            is RazorpayCallbackResult.Error -> {
                _uiState.update { 
                    it.copy(
                        isProcessing = false, 
                        paymentResult = PaymentResult.Failed(callbackResult.description, referenceId)
                    ) 
                }
            }
        }
    }

    private suspend fun verifyPayment(
        transactionId: String, 
        referenceId: String, 
        signature: String?,
        amount: Double,
        order: Order?
    ) {
        val verificationRes = paymentRepository.verifyPayment(
            transactionId = transactionId,
            referenceId = referenceId,
            signature = signature,
            expectedAmount = amount
        )

        if (verificationRes is Resource.Success && verificationRes.data == true) {
            // The Cloud Function verified payment AND updated Firestore status to CONFIRMED.
            // We just clear the cart and finish.
            if (order != null) {
                val completedOrder = order.copy(
                    orderId = referenceId,
                    paymentStatus = com.foodfusionai.app.data.models.order.PaymentStatus.SUCCESS,
                    orderStatus = com.foodfusionai.app.data.models.order.OrderStatus.CONFIRMED,
                    paymentReference = transactionId,
                    totalAmount = amount
                )
                clearCartAfterSuccess(completedOrder)
            } else {
                _uiState.update { it.copy(isProcessing = false, error = "Order reference lost locally, but payment succeeded.") }
            }
        } else {
            _uiState.update { it.copy(isProcessing = false, error = "Payment verification failed or mismatch amount.") }
        }
    }

    private suspend fun clearCartAfterSuccess(createdOrder: Order) {
        try {
            cartRepository.clearCart()
            _uiState.update { 
                it.copy(
                    isProcessing = false, 
                    orderCreated = createdOrder,
                    isCartCleared = true
                ) 
            }
        } catch (e: Exception) {
            // Edge case: Order created but cart failed to clear.
            // Still mark as success for UI but log error. Cart will just have items.
            _uiState.update { 
                it.copy(
                    isProcessing = false, 
                    orderCreated = createdOrder,
                    error = "Order placed, but cart clearing failed locally."
                ) 
            }
        }
    }

    class Factory(
        private val paymentRepository: PaymentRepository,
        private val orderRepository: OrderRepository,
        private val cartRepository: CartRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PaymentViewModel::class.java)) {
                return PaymentViewModel(paymentRepository, orderRepository, cartRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
