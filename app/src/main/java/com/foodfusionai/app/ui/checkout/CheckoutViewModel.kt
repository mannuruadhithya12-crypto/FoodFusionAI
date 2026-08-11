package com.foodfusionai.app.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.models.Address
import com.foodfusionai.app.data.models.Coupon
import com.foodfusionai.app.data.models.DeliveryValidationResult
import com.foodfusionai.app.data.repository.AddressRepository
import com.foodfusionai.app.data.repository.AddressRepositoryImpl
import com.foodfusionai.app.data.repository.CartRepository
import com.foodfusionai.app.data.repository.CartRepositoryImpl
import com.foodfusionai.app.data.repository.CouponRepository
import com.foodfusionai.app.data.repository.CouponRepositoryImpl
import com.foodfusionai.app.data.repository.DeliveryValidationRepository
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel governing checkout validation, delivery validation, and coupon checks.
 *
 * Phase 16 changes:
 *  - Delivery fee is now obtained from the server via [DeliveryValidationRepository].
 *  - When the selected address has coordinates, we call `validateDeliveryLocation`
 *    to get the authoritative fee and confirm the address is in range.
 *  - If the address has no coordinates (manually entered), we fall back to the
 *    local flat-fee logic and show a warning.
 */
class CheckoutViewModel(
    private val cartRepository: CartRepository,
    private val addressRepository: AddressRepository,
    private val couponRepository: CouponRepository,
    private val deliveryValidationRepository: DeliveryValidationRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null,
    // Fallback flat fee used when coordinates are unavailable
    private val deliveryFeeThreshold: Double = 500.0,
    private val deliveryFeeDefault: Double = 40.0
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    // ID of the restaurant for the current cart — set by CartFragment before navigating
    var restaurantId: String = ""

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
                    val currentSelected = _uiState.value.selectedAddress
                    if (currentSelected == null || addresses.none { it.id == currentSelected.id }) {
                        _uiState.update { it.copy(selectedAddress = defaultAddress) }
                        validateDeliveryAndRecalculate()
                    }
                }
            }

            // Observe Cart items
            cartRepository.getAllCartItems().collect { cartItems ->
                _uiState.update { it.copy(isLoading = false, cartItems = cartItems) }
                validateDeliveryAndRecalculate()
            }
        }
    }

    fun selectAddress(address: Address) {
        _uiState.update { it.copy(selectedAddress = address) }
        validateDeliveryAndRecalculate()
    }

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
                        it.copy(isLoading = false, appliedCoupon = coupon, couponCode = code, error = null)
                    }
                    validateDeliveryAndRecalculate()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Minimum order of ₹${coupon.minOrderAmount} required for coupon.")
                    }
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = (res as? Resource.Error)?.message ?: "Invalid coupon.")
                }
            }
        }
    }

    fun removeCoupon() {
        _uiState.update { it.copy(appliedCoupon = null, couponCode = "") }
        validateDeliveryAndRecalculate()
    }

    fun updateDeliveryInstructions(text: String) {
        if (text.length <= 120) _uiState.update { it.copy(deliveryInstructions = text) }
    }

    fun validateAndProceed() {
        val state = _uiState.value
        if (state.cartItems.isEmpty()) {
            _uiState.update { it.copy(validationMessage = "Cannot checkout: your cart is empty.") }
            return
        }
        if (state.selectedAddress == null) {
            _uiState.update { it.copy(validationMessage = "Cannot checkout: please select a delivery address.") }
            return
        }
        if (state.deliveryValidation?.isDeliverable == false) {
            _uiState.update { it.copy(validationMessage = "This address is outside the delivery range: ${state.deliveryValidation.reason}") }
            return
        }
        if (state.payableTotal <= 0.0) {
            _uiState.update { it.copy(validationMessage = "Cannot checkout: grand total is invalid.") }
            return
        }
        _uiState.update { it.copy(checkoutValidationPassed = true, validationMessage = "Proceed to payment") }
    }

    fun resetValidationMessage() {
        _uiState.update { it.copy(validationMessage = null) }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Calls the `validateDeliveryLocation` Cloud Function when the address has
     * real coordinates; falls back to local flat-fee when it doesn't.
     */
    private fun validateDeliveryAndRecalculate() {
        val state = _uiState.value
        val subtotal = state.cartItems.sumOf { it.price * it.quantity }
        val address = state.selectedAddress

        if (address != null && address.hasCoordinates && restaurantId.isNotBlank()) {
            scope.launch {
                _uiState.update { it.copy(isValidatingDelivery = true) }
                val result = deliveryValidationRepository.validateDelivery(
                    restaurantId = restaurantId,
                    customerLat  = address.latitude,
                    customerLon  = address.longitude
                )
                val validation = (result as? Resource.Success)?.data
                val fee = validation?.deliveryFee ?: localFallbackFee(subtotal)
                _uiState.update { it.copy(isValidatingDelivery = false, deliveryValidation = validation) }
                recalculateTotals(subtotal, fee)
            }
        } else {
            recalculateTotals(subtotal, localFallbackFee(subtotal))
        }
    }

    private fun localFallbackFee(subtotal: Double): Double =
        if (subtotal >= deliveryFeeThreshold || subtotal == 0.0) 0.0 else deliveryFeeDefault

    private fun recalculateTotals(subtotal: Double, deliveryFee: Double) {
        var discount = 0.0
        val coupon = _uiState.value.appliedCoupon
        if (coupon != null && subtotal >= coupon.minOrderAmount) {
            val potential = (subtotal * coupon.discountPercentage) / 100.0
            discount = if (coupon.maxDiscountAmount > 0.0) minOf(potential, coupon.maxDiscountAmount) else potential
        }
        val payable = maxOf(0.0, subtotal + deliveryFee - discount)
        _uiState.update { it.copy(subtotal = subtotal, deliveryFee = deliveryFee, discount = discount, payableTotal = payable) }
    }

    class Factory(
        private val cartRepository: CartRepository = CartRepositoryImpl(),
        private val addressRepository: AddressRepository = AddressRepositoryImpl(),
        private val couponRepository: CouponRepository = CouponRepositoryImpl(),
        private val deliveryValidationRepository: DeliveryValidationRepository = DeliveryValidationRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CheckoutViewModel::class.java)) {
                return CheckoutViewModel(cartRepository, addressRepository, couponRepository, deliveryValidationRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
