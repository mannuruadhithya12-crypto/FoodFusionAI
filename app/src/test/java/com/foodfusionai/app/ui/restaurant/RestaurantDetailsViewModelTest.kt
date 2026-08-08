package com.foodfusionai.app.ui.restaurant

import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.data.repository.RestaurantRepository
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RestaurantDetailsViewModelTest {

    private lateinit var mockRepository: MockRestaurantRepository
    private lateinit var viewModel: RestaurantDetailsViewModel

    @Before
    fun setUp() {
        mockRepository = MockRestaurantRepository()
    }

    @Test
    fun `test loading state when fetching initial data`() = runBlocking {
        // We set repo to hold or check intermediate loading state if needed.
        // Initially, state is loading = false.
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `test successful restaurant data fetch`() = runBlocking {
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        viewModel.loadRestaurantData("r1")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNotNull(state.restaurant)
        assertEquals("Pizza Palace", state.restaurant?.name)
        assertEquals(1, state.categories.size)
        assertEquals("Pizza", state.categories.first().name)
        assertEquals(1, state.menu.size)
    }

    @Test
    fun `test invalid restaurant ID check`() = runBlocking {
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        viewModel.loadRestaurantData("")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Invalid restaurant information.", state.error)
    }

    @Test
    fun `test restaurant not found`() = runBlocking {
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        viewModel.loadRestaurantData("r999") // ID that doesn't exist

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Restaurant not found.", state.error)
    }

    @Test
    fun `test repository error propagation`() = runBlocking {
        mockRepository.shouldReturnError = true
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        viewModel.loadRestaurantData("r1")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Mock error loading restaurant", state.error)
    }

    @Test
    fun `test empty menu results`() = runBlocking {
        mockRepository.mockMenu = emptyList()
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        viewModel.loadRestaurantData("r1")

        val state = viewModel.uiState.value
        assertEquals(0, state.menu.size)
        assertEquals(0, state.categories.size)
    }

    @Test
    fun `test menu filter by category`() = runBlocking {
        mockRepository.mockMenu = listOf(
            Food("f1", "r1", "c1", "Pizza A", price = 100.0),
            Food("f2", "r1", "c2", "Burger B", price = 200.0)
        )
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        viewModel.loadRestaurantData("r1")

        viewModel.selectCategoryFilter("c1")

        val state = viewModel.uiState.value
        assertEquals(1, state.filteredMenu.size)
        assertEquals("f1", state.filteredMenu.first().id)
    }

    @Test
    fun `test menu search matching description or name`() = runBlocking {
        mockRepository.mockMenu = listOf(
            Food("f1", "r1", "c1", "Margherita Pizza", price = 100.0),
            Food("f2", "r1", "c1", "Farmhouse Pizza", price = 200.0)
        )
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        viewModel.loadRestaurantData("r1")

        viewModel.updateMenuSearchInput("margh")
        
        // Wait/trigger search debouncing checks
        val state = viewModel.uiState.value
        assertEquals(1, state.filteredMenu.size)
        assertEquals("Margherita Pizza", state.filteredMenu.first().name)
    }

    @Test
    fun `test combined category and search filter`() = runBlocking {
        mockRepository.mockMenu = listOf(
            Food("f1", "r1", "c1", "Special Veg Pizza", price = 100.0),
            Food("f2", "r1", "c1", "Cheese Pizza", price = 200.0),
            Food("f3", "r1", "c2", "Special Burger", price = 150.0)
        )
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        viewModel.loadRestaurantData("r1")

        viewModel.selectCategoryFilter("c1")
        viewModel.updateMenuSearchInput("special")

        val state = viewModel.uiState.value
        assertEquals(1, state.filteredMenu.size)
        assertEquals("Special Veg Pizza", state.filteredMenu.first().name)
    }

    @Test
    fun `test open status indicators`() = runBlocking {
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        viewModel.loadRestaurantData("r1") // isOpen = true in mock
        assertTrue(viewModel.uiState.value.isRestaurantOpen)
    }

    @Test
    fun `test closed status indicators`() = runBlocking {
        mockRepository.mockRestaurant = mockRepository.mockRestaurant?.copy(isOpen = false)
        viewModel = RestaurantDetailsViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        viewModel.loadRestaurantData("r1")
        assertFalse(viewModel.uiState.value.isRestaurantOpen)
    }

    class MockRestaurantRepository : RestaurantRepository {
        var shouldReturnError = false
        var mockRestaurant: Restaurant? = Restaurant("r1", "Pizza Palace", isOpen = true)
        var mockMenu = listOf(Food("f1", "r1", "c1", "Margherita Pizza", price = 249.0))
        var mockCategories = listOf(Category("c1", "Pizza"))

        override suspend fun getRestaurantById(id: String): Resource<Restaurant?> {
            return if (shouldReturnError) {
                Resource.Error("Mock error loading restaurant")
            } else {
                Resource.Success(if (id == "r1") mockRestaurant else null)
            }
        }

        override suspend fun getMenuByRestaurant(restaurantId: String): Resource<List<Food>> {
            return if (shouldReturnError) {
                Resource.Error("Mock error loading menu")
            } else {
                Resource.Success(mockMenu)
            }
        }

        override suspend fun getCategories(): Resource<List<Category>> {
            return if (shouldReturnError) {
                Resource.Error("Mock error loading categories")
            } else {
                Resource.Success(mockCategories)
            }
        }

        override suspend fun getFoodById(id: String): Resource<Food?> {
            return Resource.Success(null)
        }
    }
}
