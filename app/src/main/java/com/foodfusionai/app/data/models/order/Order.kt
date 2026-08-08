package com.foodfusionai.app.data.models.order

import com.foodfusionai.app.data.models.Address

data class Order(
    val orderId: String = "",
    val userId: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val items: List<OrderItem> = emptyList(),
    
    // Pricing Snapshot
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val discount: Double = 0.0,
    val couponDiscount: Double = 0.0,
    val totalAmount: Double = 0.0,
    
    // Address Snapshot
    val addressSnapshot: AddressSnapshot? = null,
    val deliveryInstructions: String = "",
    
    // Payment & Status
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val paymentReference: String = "",
    val orderStatus: OrderStatus = OrderStatus.PENDING_PAYMENT,
    
    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AddressSnapshot(
    val id: String = "",
    val type: String = "",
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val instructions: String = ""
) {
    companion object {
        fun fromAddress(addr: Address): AddressSnapshot {
            return AddressSnapshot(
                id = addr.id,
                type = addr.type,
                street = addr.street,
                city = addr.city,
                state = addr.state,
                zipCode = addr.zipCode,
                instructions = addr.instructions
            )
        }
    }
}
