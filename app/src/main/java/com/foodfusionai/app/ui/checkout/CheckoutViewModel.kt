package com.foodfusionai.app.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.models.Address
import com.foodfusionai.app.data.models.Coupon
import com.foodfusionai.app.data.repository.AddressRepository
import com.foodfusionai.app.data.repository.AddressRepositoryImpl
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
 * ViewModel governing checkout validation foundations, delivery directions inputs, and coupon checks.
 */
class CheckoutViewModel(
    private val cartRepository: CartRepository,
    private val addressRepository: AddressRepository,
    private val couponRepository: CouponRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null,
    private val deliveryFeeThreshold: Double = 500.0,
    private val deliveryFeeDefault: Double = 40.0
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    init {
        loadCheckoutBaseData()
    }

    fun loadCheckoutBaseData() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Observe Addresses
            scope.launch {
                addressRepository.observeAddresses().collect { addrRes ->
                    val addresses = (addrRes as? Resource.Success)?.data ?: emptyList()
                    val defaultAddress = addresses.find { it.isDefault } ?: addresses.firstOrNull()
                    
                    // We only want to set the selected address once when loading, 
                    // or if the currently selected address was deleted.
                    val currentSelected = _uiState.value.selectedAddress
                    if (currentSelected == null || addresses.none { it.id == currentSelected.id }) {
                        _uiState.update { it.copy(selectedAddress = defaultAddress) }
                        recalculateTotals()
                    }
                }
            }

            // Observe Cart items
            cartRepository.getAllCartItems().collect { cartItems ->
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        cartItems = cartItems
                    ) 
                }
                recalculateTotals()
            }
        }
    }

    /**
     * Selects specific delivery address destination.
     */
    fun selectAddress(address: Address) {
        _uiState.update { it.copy(selectedAddress = address) }
        recalculateTotals()
    }

    /**
     * Validates and applies coupon code.
     */
    fun applyCoupon(code: String) {
        if (code.isBlank()) return

        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val res = couponRepository.validateCoupon(code)

            if (res is Resource.Success && res.data != null) {
                val coupon = res.data
                val subtotal = _uiState.value.subtotal

                if (subtotal >= coupon.minOrderAmount) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            appliedCoupon = coupon,
                            couponCode = code,
                            error = null
                        ) 
                    }
                    recalculateTotals()
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Minimum order of ₹${coupon.minOrderAmount} required for coupon."
                        ) 
                    }
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = (res as? Resource.Error)?.message ?: "Invalid coupon."
                    ) 
                }
            }
        }
    }

    /**
     * Removes currently applied coupon.
     */
    fun removeCoupon() {
        _uiState.update { it.copy(appliedCoupon = null, couponCode = "") }
        recalculateTotals()
    }

    /**
     * Captures optional delivery instruction directions (Max 120 characters limit).
     */
    fun updateDeliveryInstructions(text: String) {
        if (text.length <= 120) {
            _uiState.update { it.copy(deliveryInstructions = text) }
        }
    }

    /**
     * Runs checkout verification foundation.
     */
    fun validateAndProceed() {
        val state = _uiState.value
        val subtotal = state.subtotal
        
        val isCartValid = state.cartItems.isNotEmpty()
        val isAddressValid = state.selectedAddress != null
        val isTotalValid = state.payableTotal > 0.0

        if (!isCartValid) {
            _uiState.update { it.copy(validationMessage = "Cannot checkout: your cart is empty.") }
            return
        }

        if (!isAddressValid) {
            _uiState.update { it.copy(validationMessage = "Cannot checkout: please select a delivery address.") }
            return
        }

        if (!isTotalValid) {
            _uiState.update { it.copy(validationMessage = "Cannot checkout: grand total is invalid.") }
            return
        }

        // Success checkpoint reached
        _uiState.update { 
            it.copy(
                checkoutValidationPassed = true,
                validationMessage = "Checkout validation successful — order placement will be enabled in Phase 6."
            ) 
        }
    }

    fun resetValidationMessage() {
        _uiState.update { it.copy(validationMessage = null) }
    }

    private fun recalculateTotals() {
        val state = _uiState.value
        val subtotal = state.cartItems.sumOf { it.price * it.quantity }
        val deliveryFee = if (subtotal >= deliveryFeeThreshold || subtotal == 0.0) 0.0 else deliveryFeeDefault

        var discount = 0.0
        val coupon = state.appliedCoupon
        if (coupon != null && subtotal >= coupon.minOrderAmount) {
            val potentialDiscount = (subtotal * coupon.discountPercentage) / 100.0
            discount = if (coupon.maxDiscountAmount > 0.0) {
                minOf(potentialDiscount, coupon.maxDiscountAmount)
            } else {
                potentialDiscount
            }
        }

        val payable = maxOf(0.0, subtotal + deliveryFee - discount)

        _uiState.update {
            it.copy(
                subtotal = subtotal,
                deliveryFee = deliveryFee,
                discount = discount,
                payableTotal = payable
            )
        }
    }

    /**
     * Factory class.
     */
    class Factory(
        private val cartRepository: CartRepository = CartRepositoryImpl(),
        private val addressRepository: AddressRepository = AddressRepositoryImpl(),
        private val couponRepository: CouponRepository = CouponRepositoryImpl()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CheckoutViewModel::class.java)) {
                return CheckoutViewModel(cartRepository, addressRepository, couponRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
