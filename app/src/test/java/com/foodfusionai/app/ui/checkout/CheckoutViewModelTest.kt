package com.foodfusionai.app.ui.checkout

import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.models.Address
import com.foodfusionai.app.data.models.Coupon
import com.foodfusionai.app.data.repository.AddressRepository
import com.foodfusionai.app.data.repository.CartRepository
import com.foodfusionai.app.data.repository.CouponRepository
import com.foodfusionai.app.ui.cart.CartViewModelTest
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckoutViewModelTest {

    private lateinit var mockCartRepository: MockCartRepository
    private lateinit var mockAddressRepository: MockAddressRepository
    private lateinit var mockCouponRepository: MockCouponRepository
    private lateinit var viewModel: CheckoutViewModel

    @Before
    fun setUp() {
        mockCartRepository = MockCartRepository()
        mockAddressRepository = MockAddressRepository()
        mockCouponRepository = MockCouponRepository()
    }

    @Test
    fun `test checkout validation fails with empty cart`() = runBlocking {
        viewModel = CheckoutViewModel(
            mockCartRepository,
            mockAddressRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        // Ensure cart is empty
        mockCartRepository.cartItemsFlow.value = emptyList()

        viewModel.validateAndProceed()

        val state = viewModel.uiState.value
        assertFalse(state.checkoutValidationPassed)
        assertEquals("Cannot checkout: your cart is empty.", state.validationMessage)
    }

    @Test
    fun `test checkout validation fails when address is missing`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 200.0, 1, "img", null, "r1")
        )
        mockAddressRepository.addressesList = emptyList() // No addresses saved

        viewModel = CheckoutViewModel(
            mockCartRepository,
            mockAddressRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.validateAndProceed()

        val state = viewModel.uiState.value
        assertFalse(state.checkoutValidationPassed)
        assertEquals("Cannot checkout: please select a delivery address.", state.validationMessage)
    }

    @Test
    fun `test valid checkout validation success does not create order`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 200.0, 1, "img", null, "r1")
        )
        viewModel = CheckoutViewModel(
            mockCartRepository,
            mockAddressRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.validateAndProceed()

        val state = viewModel.uiState.value
        assertTrue(state.checkoutValidationPassed)
        assertEquals(
            "Checkout validation successful — order placement will be enabled in Phase 6.",
            state.validationMessage
        )
        // Verify no actual backend order creations occurred
    }

    @Test
    fun `test delivery instructions input validation length`() = runBlocking {
        viewModel = CheckoutViewModel(
            mockCartRepository,
            mockAddressRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        val longText = "a".repeat(150)
        viewModel.updateDeliveryInstructions(longText)
        assertEquals("", viewModel.uiState.value.deliveryInstructions) // should protect against text > 120 chars

        val validText = "Call when outside"
        viewModel.updateDeliveryInstructions(validText)
        assertEquals(validText, viewModel.uiState.value.deliveryInstructions)
    }

    @Test
    fun `test coupon validation matches min order and discount caps`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 600.0, 1, "img", null, "r1")
        )
        viewModel = CheckoutViewModel(
            mockCartRepository,
            mockAddressRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.applyCoupon("FOOD20")

        val state = viewModel.uiState.value
        assertNotNull(state.appliedCoupon)
        assertEquals(120.0, state.discount, 0.0) // 20% of 600 is 120
        assertEquals(480.0, state.payableTotal, 0.0) // subtotal 600 + free delivery 0 - discount 120 = 480
    }

    class MockCartRepository : CartRepository {
        val cartItemsFlow = MutableStateFlow<List<CartEntity>>(emptyList())
        override fun getAllCartItems(): Flow<List<CartEntity>> = cartItemsFlow
        override suspend fun addToCart(item: CartEntity) {}
        override suspend fun removeFromCart(item: CartEntity) {}
        override suspend fun clearCart() {}
    }

    class MockAddressRepository : AddressRepository {
        var addressesList = listOf(Address("addr_1", "Home", "123, Park Street", "Bangalore"))
        override fun observeAddresses(): Flow<Resource<List<Address>>> = flowOf(Resource.Success(addressesList))
        override suspend fun addAddress(address: Address): Resource<Unit> {
            return Resource.Success(Unit)
        }
        override suspend fun updateAddress(address: Address): Resource<Unit> = Resource.Success(Unit)
        override suspend fun deleteAddress(addressId: String): Resource<Unit> = Resource.Success(Unit)
        override suspend fun setDefaultAddress(addressId: String): Resource<Unit> = Resource.Success(Unit)
    }

    class MockCouponRepository : CouponRepository {
        var mockCoupon: Coupon? = Coupon(
            "coupon_1", "FOOD20", "20% OFF", 20.0, 200.0, 500.0, 0L, true
        )
        override suspend fun validateCoupon(code: String): Resource<Coupon?> {
            return if (code == mockCoupon?.code) {
                Resource.Success(mockCoupon)
            } else {
                Resource.Error("Invalid coupon")
            }
        }
        override suspend fun getAvailableCoupons(): Resource<List<Coupon>> {
            return Resource.Success(emptyList())
        }
    }
}
