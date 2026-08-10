package com.foodfusionai.driver.data.models

import androidx.annotation.Keep

@Keep
enum class OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_PROCESSING,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    PAYMENT_FAILED
}

@Keep
enum class PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    REFUNDED
}

@Keep
enum class PaymentMethod {
    UPI,
    CARD,
    NET_BANKING,
    WALLET,
    CASH_ON_DELIVERY
}

@Keep
data class Order(
    val orderId: String = "",
    val userId: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val items: List<OrderItem> = emptyList(),
    
    // Pricing
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val discount: Double = 0.0,
    val couponDiscount: Double = 0.0,
    val totalAmount: Double = 0.0,
    
    // Address snapshot
    val addressSnapshot: AddressSnapshot? = null,
    val deliveryInstructions: String = "",
    
    // Statuses
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val paymentReference: String = "",
    val orderStatus: OrderStatus = OrderStatus.PENDING_PAYMENT,
    
    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val estimatedDeliveryAt: Long? = null,
    val pickedUpAt: Long? = null,
    val deliveredAt: Long? = null,
    
    // Verification
    val deliveryOtp: String = "",
    
    // Tracking & Partner
    val deliveryPartner: DeliveryPartner? = null,
    val deliveryLocation: DeliveryLocation? = null,
    val statusHistory: List<OrderStatusHistory> = emptyList()
)

@Keep
data class OrderItem(
    val foodId: String = "",
    val foodName: String = "",
    val quantity: Int = 0,
    val unitPrice: Double = 0.0
)

@Keep
data class DeliveryPartner(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val vehicleType: String = "",
    val vehicleNumber: String = ""
)

@Keep
data class DeliveryLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val heading: Double = 0.0,
    val speed: Double = 0.0,
    val accuracy: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Keep
data class OrderStatusHistory(
    val status: OrderStatus = OrderStatus.PENDING_PAYMENT,
    val previousStatus: OrderStatus? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val message: String = ""
)

@Keep
data class AddressSnapshot(
    val id: String = "",
    val type: String = "",
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val instructions: String = ""
)
