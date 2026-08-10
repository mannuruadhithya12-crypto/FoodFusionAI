package com.foodfusionai.app.ui.order

import com.foodfusionai.app.data.models.order.Order
import com.foodfusionai.app.data.models.order.OrderStatus
import com.foodfusionai.app.data.models.order.DeliveryLocation

data class OrderTrackingUiState(
    val order: Order? = null,
    val driverLocation: DeliveryLocation? = null,
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val isCancelling: Boolean = false,
    val cancelError: String? = null
) {
    val canCancel: Boolean
        get() {
            val status = order?.orderStatus ?: return false
            return status == OrderStatus.CONFIRMED || status == OrderStatus.PREPARING || 
                   status == OrderStatus.PENDING_PAYMENT || status == OrderStatus.PAYMENT_PROCESSING ||
                   status == OrderStatus.PAYMENT_FAILED
        }
}
