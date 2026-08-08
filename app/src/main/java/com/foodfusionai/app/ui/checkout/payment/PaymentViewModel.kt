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
            paymentRepository.initiatePayment(request).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.update { it.copy(isProcessing = true, error = null) }
                    is Resource.Success -> {
                        val result = res.data
                        if (result is PaymentResult.RequiresAction) {
                            // The fragment will observe this and launch Checkout
                            _uiState.update { it.copy(isProcessing = false, paymentResult = result) }
                        } else if (result is PaymentResult.Success) {
                            verifyAndCreateOrder(result.transactionId, result.referenceId, result.signature, result.amount)
                        } else {
                            // Failed or Cancelled - Cart is naturally preserved as we do not clear it
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
    }

    /**
     * Called by the fragment after receiving Razorpay callback.
     */
    fun submitRazorpayResult(callbackResult: RazorpayCallbackResult, amount: Double, referenceId: String) {
        when (callbackResult) {
            is RazorpayCallbackResult.Success -> {
                _uiState.update { it.copy(isProcessing = true) }
                coroutineScope.launch {
                    verifyAndCreateOrder(
                        transactionId = callbackResult.paymentId,
                        referenceId = referenceId,
                        signature = callbackResult.signature,
                        amount = amount
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

    private suspend fun verifyAndCreateOrder(
        transactionId: String, 
        referenceId: String, 
        signature: String?,
        amount: Double
    ) {
        val verificationRes = paymentRepository.verifyPayment(
            transactionId = transactionId,
            referenceId = referenceId,
            signature = signature,
            expectedAmount = amount
        )

        if (verificationRes is Resource.Success && verificationRes.data == true) {
            val orderToCreate = pendingOrderSnapshot?.copy(
                paymentReference = referenceId,
                paymentStatus = com.foodfusionai.app.data.models.order.PaymentStatus.SUCCESS,
                orderStatus = com.foodfusionai.app.data.models.order.OrderStatus.CONFIRMED,
                totalAmount = amount // Ensuring exact verified amount
            )

            if (orderToCreate == null) {
                _uiState.update { it.copy(isProcessing = false, error = "Order snapshot missing.") }
                return
            }

            orderRepository.createOrder(orderToCreate).collect { orderRes ->
                when (orderRes) {
                    is Resource.Loading -> { /* already processing */ }
                    is Resource.Success -> {
                        clearCartAfterSuccess(orderRes.data!!)
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isProcessing = false, error = "Order creation failed: ${orderRes.message}") }
                    }
                    is Resource.Empty -> {
                        _uiState.update { it.copy(isProcessing = false, error = "Empty order response") }
                    }
                }
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
