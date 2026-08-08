package com.foodfusionai.app.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.repository.CartRepository
import com.foodfusionai.app.data.repository.CartRepositoryImpl
import com.foodfusionai.app.data.repository.CouponRepository
import com.foodfusionai.app.data.repository.CouponRepositoryImpl
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing Cart items, quantities, clear confirmations, and invoicing subtotal parameters.
 */
class CartViewModel(
    private val cartRepository: CartRepository,
    private val couponRepository: CouponRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null,
    private val deliveryFeeThreshold: Double = 500.0,
    private val deliveryFeeDefault: Double = 40.0
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    init {
        observeCartData()
    }

    private fun observeCartData() {
        scope.launch {
            cartRepository.getAllCartItems().collect { cartItems ->
                _uiState.update { it.copy(items = cartItems, itemCount = cartItems.sumOf { item -> item.quantity }) }
                recalculateInvoice()
            }
        }
    }

    /**
     * Increments item quantity, enforcing upper limit of 10.
     */
    fun increaseQuantity(item: CartEntity) {
        if (item.quantity < 10) {
            scope.launch {
                cartRepository.addToCart(item.copy(quantity = 1))
            }
        }
    }

    /**
     * Decrements item quantity, enforcing lower limit of 1.
     */
    fun decreaseQuantity(item: CartEntity) {
        if (item.quantity > 1) {
            scope.launch {
                // To decrease quantity, we update Room item with direct decrement
                cartRepository.addToCart(item.copy(quantity = -1))
            }
        }
    }

    /**
     * Removes an item from the cart.
     */
    fun removeItem(item: CartEntity) {
        scope.launch {
            cartRepository.removeFromCart(item)
        }
    }

    /**
     * Clears all items in the cart.
     */
    fun clearCart() {
        scope.launch {
            cartRepository.clearCart()
            _uiState.update { it.copy(appliedCoupon = null) }
        }
    }

    /**
     * Attempts to validate and apply a coupon to current invoice.
     */
    fun applyCoupon(code: String) {
        if (code.isBlank()) return

        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val subtotal = _uiState.value.subtotal
            val res = couponRepository.validateCoupon(code, subtotal)
            
            if (res is Resource.Success && res.data != null) {
                val coupon = res.data
                val subtotal = _uiState.value.subtotal
                
                // Validate coupon minimum order requirements
                if (subtotal >= coupon.minOrderAmount) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            appliedCoupon = coupon,
                            error = null
                        ) 
                    }
                    recalculateInvoice()
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Minimum order of ₹${coupon.minOrderAmount} required for this coupon."
                        ) 
                    }
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = (res as? Resource.Error)?.message ?: "Invalid coupon code."
                    ) 
                }
            }
        }
    }

    /**
     * Removes current applied coupon.
     */
    fun removeCoupon() {
        _uiState.update { it.copy(appliedCoupon = null) }
        recalculateInvoice()
    }

    private fun recalculateInvoice() {
        val state = _uiState.value
        val items = state.items

        val subtotal = items.sumOf { it.price * it.quantity }
        val deliveryFee = if (subtotal >= deliveryFeeThreshold || subtotal == 0.0) 0.0 else deliveryFeeDefault

        var couponDiscount = 0.0
        val coupon = state.appliedCoupon
        if (coupon != null && subtotal >= coupon.minOrderAmount) {
            val potentialDiscount = (subtotal * coupon.discountPercentage) / 100.0
            couponDiscount = if (coupon.maxDiscountAmount > 0.0) {
                minOf(potentialDiscount, coupon.maxDiscountAmount)
            } else {
                potentialDiscount
            }
        }

        val grandTotal = maxOf(0.0, subtotal + deliveryFee - couponDiscount)

        _uiState.update {
            it.copy(
                subtotal = subtotal,
                deliveryFee = deliveryFee,
                couponDiscount = couponDiscount,
                grandTotal = grandTotal,
                isEmpty = items.isEmpty(),
                canCheckout = items.isNotEmpty()
            )
        }
    }

    /**
     * Factory class.
     */
    class Factory(
        private val cartRepository: CartRepository = CartRepositoryImpl(),
        private val couponRepository: CouponRepository = CouponRepositoryImpl()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CartViewModel::class.java)) {
                return CartViewModel(cartRepository, couponRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
