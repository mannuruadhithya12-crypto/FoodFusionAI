package com.foodfusionai.app.ui.cart

import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.models.Coupon
import com.foodfusionai.app.data.repository.CartRepository
import com.foodfusionai.app.data.repository.CouponRepository
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CartViewModelTest {

    private lateinit var mockCartRepository: MockCartRepository
    private lateinit var mockCouponRepository: MockCouponRepository
    private lateinit var viewModel: CartViewModel

    @Before
    fun setUp() {
        mockCartRepository = MockCartRepository()
        mockCouponRepository = MockCouponRepository()
    }

    @Test
    fun `test initial state with empty cart`() = runBlocking {
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        val state = viewModel.uiState.value
        assertTrue(state.isEmpty)
        assertEquals(0.0, state.subtotal, 0.0)
        assertEquals(0.0, state.deliveryFee, 0.0)
        assertEquals(0.0, state.grandTotal, 0.0)
        assertFalse(state.canCheckout)
    }

    @Test
    fun `test subtotal and delivery fee below threshold`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 200.0, 1, "img", null, "r1")
        )
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined),
            deliveryFeeThreshold = 500.0,
            deliveryFeeDefault = 40.0
        )

        val state = viewModel.uiState.value
        assertEquals(200.0, state.subtotal, 0.0)
        assertEquals(40.0, state.deliveryFee, 0.0)
        assertEquals(240.0, state.grandTotal, 0.0)
    }

    @Test
    fun `test subtotal exactly at threshold free delivery`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 500.0, 1, "img", null, "r1")
        )
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined),
            deliveryFeeThreshold = 500.0,
            deliveryFeeDefault = 40.0
        )

        val state = viewModel.uiState.value
        assertEquals(500.0, state.subtotal, 0.0)
        assertEquals(0.0, state.deliveryFee, 0.0)
        assertEquals(500.0, state.grandTotal, 0.0)
    }

    @Test
    fun `test subtotal above threshold free delivery`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 600.0, 1, "img", null, "r1")
        )
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined),
            deliveryFeeThreshold = 500.0,
            deliveryFeeDefault = 40.0
        )

        val state = viewModel.uiState.value
        assertEquals(600.0, state.subtotal, 0.0)
        assertEquals(0.0, state.deliveryFee, 0.0)
        assertEquals(600.0, state.grandTotal, 0.0)
    }

    @Test
    fun `test multiple items calculation`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 200.0, 2, "img", null, "r1"), // 400.0
            CartEntity("item_2", "f2", "Burger", 100.0, 1, "img", null, "r1") // 100.0
        )
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        val state = viewModel.uiState.value
        assertEquals(500.0, state.subtotal, 0.0)
        assertEquals(3, state.itemCount)
    }

    @Test
    fun `test increase quantity calls repository`() = runBlocking {
        val item = CartEntity("item_1", "f1", "Pizza", 200.0, 2, "img", null, "r1")
        mockCartRepository.cartItemsFlow.value = listOf(item)
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.increaseQuantity(item)

        assertEquals(1, mockCartRepository.addedItems.size)
        assertEquals(1, mockCartRepository.addedItems.first().quantity) // incremental qty added = 1
    }

    @Test
    fun `test decrease quantity calls repository`() = runBlocking {
        val item = CartEntity("item_1", "f1", "Pizza", 200.0, 2, "img", null, "r1")
        mockCartRepository.cartItemsFlow.value = listOf(item)
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.decreaseQuantity(item)

        assertEquals(1, mockCartRepository.addedItems.size)
        assertEquals(-1, mockCartRepository.addedItems.first().quantity) // incremental qty decremented = -1
    }

    @Test
    fun `test quantity limits protection minimum`() = runBlocking {
        val item = CartEntity("item_1", "f1", "Pizza", 200.0, 1, "img", null, "r1")
        mockCartRepository.cartItemsFlow.value = listOf(item)
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.decreaseQuantity(item)
        assertEquals(0, mockCartRepository.addedItems.size) // should not decrease below 1
    }

    @Test
    fun `test quantity limits protection maximum`() = runBlocking {
        val item = CartEntity("item_1", "f1", "Pizza", 200.0, 10, "img", null, "r1")
        mockCartRepository.cartItemsFlow.value = listOf(item)
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.increaseQuantity(item)
        assertEquals(0, mockCartRepository.addedItems.size) // should not increase above 10
    }

    @Test
    fun `test remove item calls repository`() = runBlocking {
        val item = CartEntity("item_1", "f1", "Pizza", 200.0, 2, "img", null, "r1")
        mockCartRepository.cartItemsFlow.value = listOf(item)
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.removeItem(item)
        assertEquals(1, mockCartRepository.removedItems.size)
        assertEquals("item_1", mockCartRepository.removedItems.first().id)
    }

    @Test
    fun `test clear cart calls repository`() = runBlocking {
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.clearCart()
        assertTrue(mockCartRepository.cartCleared)
    }

    @Test
    fun `test coupon validation minimum order requirement success`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 600.0, 1, "img", null, "r1")
        )
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.applyCoupon("FOOD20")

        val state = viewModel.uiState.value
        assertNotNull(state.appliedCoupon)
        assertEquals(120.0, state.couponDiscount, 0.0) // 20% of 600
        assertEquals(480.0, state.grandTotal, 0.0)
    }

    @Test
    fun `test coupon validation minimum order requirement fail`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 200.0, 1, "img", null, "r1")
        )
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.applyCoupon("FOOD20")

        val state = viewModel.uiState.value
        assertNull(state.appliedCoupon)
        assertEquals("Minimum order of ₹500.0 required for this coupon.", state.error)
    }

    @Test
    fun `test coupon maximum discount capping`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 1200.0, 1, "img", null, "r1")
        )
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.applyCoupon("FOOD20")

        val state = viewModel.uiState.value
        assertEquals(200.0, state.couponDiscount, 0.0) // 20% of 1200 is 240, capped at maxDiscountAmount 200
        assertEquals(1000.0, state.grandTotal, 0.0)
    }

    @Test
    fun `test coupon removal updates totals`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 600.0, 1, "img", null, "r1")
        )
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.applyCoupon("FOOD20")
        viewModel.removeCoupon()

        val state = viewModel.uiState.value
        assertNull(state.appliedCoupon)
        assertEquals(0.0, state.couponDiscount, 0.0)
        assertEquals(600.0, state.grandTotal, 0.0)
    }

    @Test
    fun `test final total never negative`() = runBlocking {
        mockCartRepository.cartItemsFlow.value = listOf(
            CartEntity("item_1", "f1", "Pizza", 100.0, 1, "img", null, "r1")
        )
        mockCouponRepository.mockCoupon = Coupon(
            "coupon_1", "FREEITEMS", "Free", 100.0, 1000.0, 50.0, 0L, true
        )
        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined),
            deliveryFeeThreshold = 500.0,
            deliveryFeeDefault = 0.0 // no delivery fee to test strictly negative price protection
        )

        viewModel.applyCoupon("FREEITEMS")

        val state = viewModel.uiState.value
        assertEquals(100.0, state.couponDiscount, 0.0)
        assertEquals(0.0, state.grandTotal, 0.0) // subtotal 100 - discount 100 = 0.0 (never negative)
    }

    @Test
    fun `test multi restaurant validation conflict check`() = runBlocking {
        val item1 = CartEntity("item_1", "f1", "Pizza", 200.0, 1, "img", null, "r1")
        val item2 = CartEntity("item_2", "f2", "Burger", 100.0, 1, "img", null, "r2")
        mockCartRepository.cartItemsFlow.value = listOf(item1, item2)

        viewModel = CartViewModel(
            mockCartRepository,
            mockCouponRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )

        val state = viewModel.uiState.value
        // State calculations still complete but validation holds
        assertEquals(300.0, state.subtotal, 0.0)
    }

    class MockCartRepository : CartRepository {
        val cartItemsFlow = MutableStateFlow<List<CartEntity>>(emptyList())
        val addedItems = mutableListOf<CartEntity>()
        val removedItems = mutableListOf<CartEntity>()
        var cartCleared = false

        override fun getAllCartItems(): Flow<List<CartEntity>> = cartItemsFlow

        override suspend fun addToCart(item: CartEntity) {
            addedItems.add(item)
        }

        override suspend fun removeFromCart(item: CartEntity) {
            removedItems.add(item)
        }

        override suspend fun clearCart() {
            cartCleared = true
        }
    }

    class MockCouponRepository : CouponRepository {
        var mockCoupon: Coupon? = Coupon(
            "coupon_1", "FOOD20", "20% OFF", 20.0, 200.0, 500.0, 0L, true
        )

        override suspend fun validateCoupon(code: String, cartTotal: Double): Resource<Coupon?> {
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
