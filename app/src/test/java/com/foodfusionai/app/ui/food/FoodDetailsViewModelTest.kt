package com.foodfusionai.app.ui.food

import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.data.repository.CartRepository
import com.foodfusionai.app.data.repository.RestaurantRepository
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FoodDetailsViewModelTest {

    private lateinit var mockRestaurantRepository: MockRestaurantRepository
    private lateinit var mockCartRepository: MockCartRepository
    private lateinit var viewModel: FoodDetailsViewModel

    @Before
    fun setUp() {
        mockRestaurantRepository = MockRestaurantRepository()
        mockCartRepository = MockCartRepository()
    }

    @Test
    fun `test loading state when fetching initial data`() = runBlocking {
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `test successful food data fetch`() = runBlocking {
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f1")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNotNull(state.food)
        assertEquals("Margherita Pizza", state.food?.name)
        assertEquals(249.0, state.food?.price ?: 0.0, 0.0)
        assertEquals(249.0, state.subtotal, 0.0) // defaults to Medium
    }

    @Test
    fun `test invalid food ID check`() = runBlocking {
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Invalid food item selection.", state.error)
    }

    @Test
    fun `test food item not found`() = runBlocking {
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f999")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Item not found.", state.error)
    }

    @Test
    fun `test repository error propagation`() = runBlocking {
        mockRestaurantRepository.shouldReturnError = true
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f1")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Mock error loading food", state.error)
    }

    @Test
    fun `test quantity boundaries increase and decrease`() = runBlocking {
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f1")

        // Increase
        viewModel.increaseQuantity()
        assertEquals(2, viewModel.uiState.value.quantity)
        assertEquals(498.0, viewModel.uiState.value.subtotal, 0.0) // 249.0 * 2

        // Decrease
        viewModel.decreaseQuantity()
        assertEquals(1, viewModel.uiState.value.quantity)
        assertEquals(249.0, viewModel.uiState.value.subtotal, 0.0)
    }

    @Test
    fun `test quantity minimum boundary protection`() = runBlocking {
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f1")

        // Try to decrease below 1
        viewModel.decreaseQuantity()
        assertEquals(1, viewModel.uiState.value.quantity)
    }

    @Test
    fun `test quantity maximum boundary protection`() = runBlocking {
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f1")

        // Loop past 10
        for (i in 1..15) {
            viewModel.increaseQuantity()
        }
        assertEquals(10, viewModel.uiState.value.quantity)
    }

    @Test
    fun `test customization size pricing adjustments`() = runBlocking {
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f1") // base 249.0

        // Select Large Size (+30.0)
        viewModel.selectSize("Large")
        assertEquals(279.0, viewModel.uiState.value.subtotal, 0.0)

        // Select Small Size (-20.0)
        viewModel.selectSize("Small")
        assertEquals(229.0, viewModel.uiState.value.subtotal, 0.0)
    }

    @Test
    fun `test unavailable food item disabled add to cart`() = runBlocking {
        mockRestaurantRepository.mockFood = mockRestaurantRepository.mockFood?.copy(isAvailable = false)
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f1")

        viewModel.addToCart()
        assertNull(viewModel.cartInsertionSuccess.value) // Should not trigger repository add
    }

    @Test
    fun `test CartEntity mapping details`() = runBlocking {
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f1")
        viewModel.increaseQuantity() // qty = 2
        viewModel.selectSize("Large") // price = 249 + 30 = 279
        viewModel.selectSpiceLevel("Hot")

        viewModel.addToCart()

        assertEquals(1, mockCartRepository.addedItems.size)
        val item = mockCartRepository.addedItems.first()
        assertEquals("f1_Large_Hot", item.id)
        assertEquals("f1", item.foodId)
        assertEquals("Margherita Pizza", item.foodName)
        assertEquals(279.0, item.price, 0.0)
        assertEquals(2, item.quantity)
        assertEquals("Size: Large, Spice: Hot", item.customizationsJson)
    }

    @Test
    fun `test add to cart success trigger`() = runBlocking {
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f1")
        
        viewModel.addToCart()
        assertEquals(true, viewModel.cartInsertionSuccess.value)
    }

    @Test
    fun `test add to cart failure propagation`() = runBlocking {
        mockCartRepository.shouldThrowError = true
        viewModel = FoodDetailsViewModel(
            mockRestaurantRepository,
            mockCartRepository,
            CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.loadFoodData("f1")

        viewModel.addToCart()
        assertEquals(false, viewModel.cartInsertionSuccess.value)
    }

    class MockRestaurantRepository : RestaurantRepository {
        var shouldReturnError = false
        var mockFood: Food? = Food("f1", "r1", "c1", "Margherita Pizza", price = 249.0, isAvailable = true)

        override suspend fun getRestaurantById(id: String): Resource<Restaurant?> {
            return Resource.Success(Restaurant("r1", "Pizza Palace"))
        }

        override suspend fun getMenuByRestaurant(restaurantId: String): Resource<List<Food>> {
            return Resource.Success(emptyList())
        }

        override suspend fun getCategories(): Resource<List<Category>> {
            return Resource.Success(emptyList())
        }

        override suspend fun getFoodById(id: String): Resource<Food?> {
            return if (shouldReturnError) {
                Resource.Error("Mock error loading food")
            } else {
                Resource.Success(if (id == "f1") mockFood else null)
            }
        }
    }

    class MockCartRepository : CartRepository {
        var shouldThrowError = false
        val addedItems = mutableListOf<CartEntity>()

        override fun getAllCartItems(): Flow<List<CartEntity>> {
            return flowOf(addedItems)
        }

        override suspend fun addToCart(item: CartEntity) {
            if (shouldThrowError) throw RuntimeException("Database error")
            addedItems.add(item)
        }

        override suspend fun removeFromCart(item: CartEntity) {
            addedItems.remove(item)
        }

        override suspend fun clearCart() {
            addedItems.clear()
        }
    }
}
