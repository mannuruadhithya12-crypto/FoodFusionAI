package com.foodfusionai.app.ui.checkout.payment

import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.models.order.Order
import com.foodfusionai.app.data.models.order.PaymentMethod
import com.foodfusionai.app.data.payment.PaymentRequest
import com.foodfusionai.app.data.payment.PaymentResult
import com.foodfusionai.app.data.repository.CartRepository
import com.foodfusionai.app.data.repository.OrderRepository
import com.foodfusionai.app.data.repository.PaymentRepository
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaymentViewModelTest {

    private lateinit var viewModel: PaymentViewModel
    private lateinit var fakePaymentRepository: FakePaymentRepository
    private lateinit var fakeOrderRepository: FakeOrderRepository
    private lateinit var fakeCartRepository: FakeCartRepository

    @Before
    fun setup() {
        fakePaymentRepository = FakePaymentRepository()
        fakeOrderRepository = FakeOrderRepository()
        fakeCartRepository = FakeCartRepository()

        viewModel = PaymentViewModel(
            fakePaymentRepository, 
            fakeOrderRepository, 
            fakeCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun `startPaymentFlow success creates order and clears cart`() = runBlocking {
        val request = PaymentRequest(500.0, "INR", PaymentMethod.UPI, "REF_123")
        val snapshot = Order(userId = "user1", totalAmount = 500.0)

        fakePaymentRepository.initiateFlow = flowOf(
            Resource.Loading,
            Resource.Success(PaymentResult.Success("TXN_1", "REF_123", 500.0))
        )
        fakePaymentRepository.verifySuccess = true
        
        val createdOrder = snapshot.copy(orderId = "ORDER_1", paymentReference = "REF_123")
        fakeOrderRepository.createFlow = flowOf(
            Resource.Loading,
            Resource.Success(createdOrder)
        )

        // Using standard CoroutineScope for testing won't suspend nicely without advanceUntilIdle, 
        // but flowOf runs synchronously in tests usually.
        viewModel.startPaymentFlow(request, snapshot)
        
        // Wait briefly for flow to collect (hack for runBlocking without advanceUntilIdle)
        kotlinx.coroutines.delay(100)

        val state = viewModel.uiState.value
        assertTrue(!state.isProcessing)
        assertNull(state.error)
        assertNotNull(state.orderCreated)
        assertTrue(state.isCartCleared)
        assertEquals("ORDER_1", state.orderCreated?.orderId)
        assertTrue(fakeCartRepository.cleared)
    }

    @Test
    fun `startPaymentFlow payment fails preserves cart and does not create order`() = runBlocking {
        val request = PaymentRequest(9999.0, "INR", PaymentMethod.UPI, "REF_123")
        val snapshot = Order(userId = "user1", totalAmount = 9999.0)

        fakePaymentRepository.initiateFlow = flowOf(
            Resource.Loading,
            Resource.Success(PaymentResult.Failed("Insufficient Funds", "REF_123"))
        )

        viewModel.startPaymentFlow(request, snapshot)
        kotlinx.coroutines.delay(100)

        val state = viewModel.uiState.value
        assertTrue(!state.isProcessing)
        assertNull(state.error)
        assertNull(state.orderCreated)
        assertTrue(!state.isCartCleared)
        assertTrue(state.paymentResult is PaymentResult.Failed)

        assertTrue(!fakeOrderRepository.created)
        assertTrue(!fakeCartRepository.cleared)
    }

    class FakePaymentRepository : PaymentRepository {
        var initiateFlow: Flow<Resource<PaymentResult>> = flowOf()
        var verifySuccess = false

        override fun initiatePayment(request: PaymentRequest): Flow<Resource<PaymentResult>> = initiateFlow
        override suspend fun verifyPayment(transactionId: String, referenceId: String, signature: String?, expectedAmount: Double): Resource<Boolean> {
            return Resource.Success(verifySuccess)
        }
    }

    class FakeOrderRepository : OrderRepository {
        var createFlow: Flow<Resource<Order>> = flowOf()
        var created = false
        override fun createOrder(order: Order): Flow<Resource<Order>> {
            created = true
            return createFlow
        }
        override fun getUserOrders(userId: String): Flow<Resource<List<Order>>> = flowOf()
        override fun getOrderById(orderId: String): Flow<Resource<Order>> = flowOf()
        override fun observeOrderById(orderId: String): Flow<Resource<Order>> = kotlinx.coroutines.flow.flowOf(Resource.Success(Order()))
    }

    class FakeCartRepository : CartRepository {
        var cleared = false
        override fun getAllCartItems(): Flow<List<CartEntity>> = flowOf(emptyList())
        override suspend fun addToCart(item: CartEntity) {}
        override suspend fun removeFromCart(item: CartEntity) {}
        override suspend fun clearCart() { cleared = true }
    }
}
