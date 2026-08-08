package com.foodfusionai.app.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.models.order.Order
import com.foodfusionai.app.data.repository.OrderRepository
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderHistoryUiState(
    val isLoading: Boolean = false,
    val orders: List<Order> = emptyList(),
    val error: String? = null
)

class OrderViewModel(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderHistoryUiState())
    val uiState: StateFlow<OrderHistoryUiState> = _uiState.asStateFlow()

    fun loadUserOrders(userId: String) {
        viewModelScope.launch {
            orderRepository.getUserOrders(userId).collect { res ->
                when (res) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true, error = null) }
                    is Resource.Success -> _uiState.update { it.copy(isLoading = false, orders = res.data ?: emptyList()) }
                    is Resource.Error -> _uiState.update { it.copy(isLoading = false, error = res.message) }
                    is Resource.Empty -> _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    class Factory(
        private val orderRepository: OrderRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OrderViewModel::class.java)) {
                return OrderViewModel(orderRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
