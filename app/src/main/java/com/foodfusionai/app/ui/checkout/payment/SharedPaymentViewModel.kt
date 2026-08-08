package com.foodfusionai.app.ui.checkout.payment

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class RazorpayCallbackResult {
    data class Success(val paymentId: String, val signature: String, val orderId: String) : RazorpayCallbackResult()
    data class Error(val code: Int, val description: String) : RazorpayCallbackResult()
}

/**
 * Activity-scoped ViewModel to bridge Razorpay SDK callbacks from MainActivity
 * to the PaymentFragment/PaymentViewModel.
 */
class SharedPaymentViewModel : ViewModel() {

    private val _razorpayResult = MutableSharedFlow<RazorpayCallbackResult>()
    val razorpayResult = _razorpayResult.asSharedFlow()

    suspend fun emitSuccess(paymentId: String, signature: String, orderId: String) {
        _razorpayResult.emit(RazorpayCallbackResult.Success(paymentId, signature, orderId))
    }

    suspend fun emitError(code: Int, description: String) {
        _razorpayResult.emit(RazorpayCallbackResult.Error(code, description))
    }
}
