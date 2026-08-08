package com.foodfusionai.app.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.repository.OrderRepository
import com.foodfusionai.app.utils.Resource
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class OrderTrackingViewModel(
    private val orderRepository: OrderRepository,
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderTrackingUiState(isLoading = true))
    val uiState: StateFlow<OrderTrackingUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null
    private var currentOrderId: String? = null

    fun startTracking(orderId: String) {
        if (currentOrderId == orderId && trackingJob?.isActive == true) return
        currentOrderId = orderId
        
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            orderRepository.observeOrderById(orderId).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = it.order == null, error = null) }
                    }
                    is Resource.Success -> {
                        val order = resource.data
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                order = order, 
                                error = null,
                                // We can deduce offline mode roughly if Firebase fails to sync, but the Resource handles errors.
                                isOffline = false 
                            ) 
                        }
                    }
                    is Resource.Error -> {
                        // If it's a network error and we have an order, we might be offline
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                error = if (it.order == null) resource.message else null,
                                isOffline = it.order != null 
                            ) 
                        }
                    }
                    is Resource.Empty -> {
                        _uiState.update { it.copy(isLoading = false, error = "Order not found") }
                    }
                }
            }
        }
    }

    fun cancelOrder() {
        val orderId = currentOrderId ?: return
        if (!_uiState.value.canCancel) return
        
        _uiState.update { it.copy(isCancelling = true, cancelError = null) }

        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "orderId" to orderId,
                    "cancelReason" to "Cancelled by user"
                )
                
                // Call Firebase Function
                functions.getHttpsCallable("cancelOrder").call(data).await()
                
                // The real-time listener will update the state automatically to CANCELLED
                _uiState.update { it.copy(isCancelling = false) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isCancelling = false, 
                        cancelError = e.message ?: "Failed to cancel order"
                    ) 
                }
            }
        }
    }

    class Factory(
        private val orderRepository: OrderRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OrderTrackingViewModel::class.java)) {
                return OrderTrackingViewModel(orderRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
