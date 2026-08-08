package com.foodfusionai.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.data.repository.CartRepository
import com.foodfusionai.app.data.repository.CartRepositoryImpl
import com.foodfusionai.app.data.repository.RestaurantRepository
import com.foodfusionai.app.data.repository.RestaurantRepositoryImpl
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel governing Individual Food details pages.
 * Enforces quantity bounds, calculates subtotal pricing, and saves to CartRepository.
 */
class FoodDetailsViewModel(
    private val restaurantRepository: RestaurantRepository,
    private val cartRepository: CartRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(FoodDetailsUiState())
    val uiState: StateFlow<FoodDetailsUiState> = _uiState.asStateFlow()

    private val _cartInsertionSuccess = MutableStateFlow<Boolean?>(null)
    val cartInsertionSuccess: StateFlow<Boolean?> = _cartInsertionSuccess.asStateFlow()

    private val _cartConflictState = MutableStateFlow<CartEntity?>(null)
    val cartConflictState: StateFlow<CartEntity?> = _cartConflictState.asStateFlow()

    /**
     * Loads food metadata and corresponding restaurant information.
     */
    fun loadFoodData(foodId: String) {
        if (foodId.isBlank() || foodId == "unknown") {
            _uiState.update { it.copy(isLoading = false, error = "Invalid food item selection.") }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val foodRes = restaurantRepository.getFoodById(foodId)
            if (foodRes is Resource.Error) {
                _uiState.update { it.copy(isLoading = false, error = foodRes.message) }
                return@launch
            }

            val food = (foodRes as? Resource.Success)?.data
            if (food == null) {
                _uiState.update { it.copy(isLoading = false, error = "Item not found.") }
                return@launch
            }

            // Retrieve restaurant details for displaying logo/address
            val restRes = restaurantRepository.getRestaurantById(food.restaurantId)
            val restaurant = (restRes as? Resource.Success)?.data

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    food = food,
                    restaurant = restaurant,
                    quantity = 1,
                    isAvailable = food.isAvailable,
                    subtotal = food.price
                )
            }
            recalculatePrice()
        }
    }

    /**
     * Increments count selection (Max limit 10).
     */
    fun increaseQuantity() {
        val currentQty = _uiState.value.quantity
        if (currentQty < 10) {
            _uiState.update { it.copy(quantity = currentQty + 1) }
            recalculatePrice()
        }
    }

    /**
     * Decrements count selection (Min limit 1).
     */
    fun decreaseQuantity() {
        val currentQty = _uiState.value.quantity
        if (currentQty > 1) {
            _uiState.update { it.copy(quantity = currentQty - 1) }
            recalculatePrice()
        }
    }

    /**
     * Updates customization selection.
     */
    fun selectSize(size: String) {
        _uiState.update { it.copy(selectedSize = size) }
        recalculatePrice()
    }

    /**
     * Updates spice preference selection.
     */
    fun selectSpiceLevel(spice: String) {
        _uiState.update { it.copy(selectedSpiceLevel = spice) }
    }

    /**
     * Saves the current product state as a CartEntity to local storage.
     */
    fun addToCart(forceClear: Boolean = false) {
        val food = _uiState.value.food ?: return
        val qty = _uiState.value.quantity
        val size = _uiState.value.selectedSize
        val spice = _uiState.value.selectedSpiceLevel
        
        if (!food.isAvailable) return

        val pricePerUnit = getPriceForSize(food.price, size)
        val customizations = "Size: $size, Spice: $spice"
        
        // Generate a deterministic cart ID mapping unique customization options
        val cartId = "${food.id}_${size}_$spice"

        val item = CartEntity(
            id = cartId,
            foodId = food.id,
            foodName = food.name,
            price = pricePerUnit,
            quantity = qty,
            imageUrl = food.imageUrl,
            customizationsJson = customizations,
            restaurantId = food.restaurantId
        )

        scope.launch {
            try {
                if (forceClear) {
                    cartRepository.clearCart()
                }
                cartRepository.addToCart(item)
                _cartInsertionSuccess.value = true
                _cartConflictState.value = null
            } catch (e: IllegalArgumentException) {
                if (e.message == "MULTI_RESTAURANT_CONFLICT") {
                    _cartConflictState.value = item
                } else {
                    _cartInsertionSuccess.value = false
                }
            } catch (e: Throwable) {
                _cartInsertionSuccess.value = false
            }
        }
    }

    fun resetConflictState() {
        _cartConflictState.value = null
    }

    fun resetCartSuccess() {
        _cartInsertionSuccess.value = null
    }

    private fun recalculatePrice() {
        val state = _uiState.value
        val food = state.food ?: return
        val basePrice = food.price
        val adjustedPrice = getPriceForSize(basePrice, state.selectedSize)
        val newSubtotal = adjustedPrice * state.quantity
        _uiState.update { it.copy(subtotal = newSubtotal) }
    }

    private fun getPriceForSize(basePrice: Double, size: String): Double {
        return when (size) {
            "Large" -> basePrice + 30.0
            "Small" -> maxOf(10.0, basePrice - 20.0) // Avoid negative prices
            else -> basePrice
        }
    }

    /**
     * Factory class.
     */
    class Factory(
        private val restaurantRepository: RestaurantRepository = RestaurantRepositoryImpl(),
        private val cartRepository: CartRepository = CartRepositoryImpl()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FoodDetailsViewModel::class.java)) {
                return FoodDetailsViewModel(restaurantRepository, cartRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
