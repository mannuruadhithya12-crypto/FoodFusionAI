package com.foodfusionai.app.data.models.order

object OrderStateMachine {
    
    /**
     * Defines valid status transitions.
     * Maps a given OrderStatus to a list of allowed next statuses.
     */
    val validTransitions = mapOf(
        OrderStatus.PENDING_PAYMENT to listOf(OrderStatus.PAYMENT_PROCESSING, OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED),
        OrderStatus.PAYMENT_PROCESSING to listOf(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED),
        OrderStatus.PAYMENT_FAILED to listOf(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED),
        OrderStatus.CONFIRMED to listOf(OrderStatus.PREPARING, OrderStatus.CANCELLED),
        OrderStatus.PREPARING to listOf(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED),
        OrderStatus.READY_FOR_PICKUP to listOf(OrderStatus.OUT_FOR_DELIVERY),
        OrderStatus.OUT_FOR_DELIVERY to listOf(OrderStatus.DELIVERED),
        OrderStatus.DELIVERED to emptyList(),
        OrderStatus.CANCELLED to emptyList()
    )

    /**
     * Checks if transitioning from [fromStatus] to [toStatus] is permitted.
     */
    fun canTransition(fromStatus: OrderStatus, toStatus: OrderStatus): Boolean {
        return validTransitions[fromStatus]?.contains(toStatus) == true
    }

    /**
     * Checks if the order can be cancelled by the customer from its current status.
     * Business Rule: Only CONFIRMED and PREPARING can be cancelled by the user.
     * (And PENDING_PAYMENT / PAYMENT_PROCESSING during checkout failures).
     */
    fun canCancel(currentStatus: OrderStatus): Boolean {
        return currentStatus == OrderStatus.CONFIRMED || currentStatus == OrderStatus.PREPARING || 
               currentStatus == OrderStatus.PENDING_PAYMENT || currentStatus == OrderStatus.PAYMENT_PROCESSING ||
               currentStatus == OrderStatus.PAYMENT_FAILED
    }
}
