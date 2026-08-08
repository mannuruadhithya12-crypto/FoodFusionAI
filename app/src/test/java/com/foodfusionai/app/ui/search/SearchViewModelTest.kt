package com.foodfusionai.app.ui.search

import com.foodfusionai.app.data.local.room.entity.RecentSearchEntity
import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.data.repository.SearchRepository
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

class SearchViewModelTest {

    private lateinit var mockRepository: MockSearchRepository
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        mockRepository = MockSearchRepository()
    }

    @Test
    fun `test initial state loads base data`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(3, state.categories.size)
        assertEquals("Pizza", state.categories.first().name)
    }

    @Test
    fun `test query suggestions prefix matches`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        // Type "bir"
        viewModel.updateQueryInput("bir")

        val state = viewModel.uiState.value
        assertEquals(3, state.suggestions.size)
        assertTrue(state.suggestions.contains("Chicken Dum Biryani"))
        assertTrue(state.suggestions.contains("Biryani House"))
        assertTrue(state.suggestions.contains("Biryani"))
    }

    @Test
    fun `test recent searches insertion and clearing`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        viewModel.submitSearchQuery("Tacos")
        
        val state1 = viewModel.uiState.value
        assertEquals(1, state1.recentSearches.size)
        assertEquals("Tacos", state1.recentSearches.first())

        // Insert duplicate term (should override/prevent duplicate)
        viewModel.submitSearchQuery("Tacos")
        val state2 = viewModel.uiState.value
        assertEquals(1, state2.recentSearches.size)

        // Clear all history
        viewModel.clearRecentSearches()
        val state3 = viewModel.uiState.value
        assertEquals(0, state3.recentSearches.size)
    }

    @Test
    fun `test recent searches single item deletion`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        viewModel.submitSearchQuery("Paneer")
        viewModel.submitSearchQuery("Dosa")
        
        val state1 = viewModel.uiState.value
        assertEquals(2, state1.recentSearches.size)

        viewModel.deleteRecentSearch("Paneer")
        val state2 = viewModel.uiState.value
        assertEquals(1, state2.recentSearches.size)
        assertEquals("Dosa", state2.recentSearches.first())
    }

    @Test
    fun `test food filter by category`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        viewModel.applyFilters(SearchFilters(category = "c1")) // Pizza category ID
        
        val state = viewModel.uiState.value
        assertEquals(1, state.foods.size)
        assertEquals("f1", state.foods.first().id) // Margherita Pizza is in c1
    }

    @Test
    fun `test food filter by vegetarian`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        viewModel.applyFilters(SearchFilters(isVegetarian = true))

        val state = viewModel.uiState.value
        assertEquals(1, state.foods.size)
        assertEquals("Margherita Pizza", state.foods.first().name) // Margherita Pizza is vegetarian
    }

    @Test
    fun `test food filter by max price`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        viewModel.applyFilters(SearchFilters(maxPrice = 200.0))

        val state = viewModel.uiState.value
        assertEquals(1, state.foods.size)
        assertEquals("Cheese Burst Burger", state.foods.first().name) // Price is 189.0 <= 200.0
    }

    @Test
    fun `test restaurant filter by delivery time`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        viewModel.applyFilters(SearchFilters(maxDeliveryTimeMinutes = 20))

        val state = viewModel.uiState.value
        assertEquals(1, state.restaurants.size)
        assertEquals("Burger Bistro", state.restaurants.first().name) // 15 mins <= 20 mins
    }

    @Test
    fun `test restaurant filter by minimum rating`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        viewModel.applyFilters(SearchFilters(minRating = 4.5))

        val state = viewModel.uiState.value
        assertEquals(2, state.restaurants.size) // Pizza Palace (4.5) & Biryani House (4.7) >= 4.5
    }

    @Test
    fun `test sorting by rating descending`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        viewModel.applySorting(SearchSort.RATING_DESC)

        val state = viewModel.uiState.value
        assertEquals("Biryani House", state.restaurants.first().name) // 4.7 is highest rating
        assertEquals("Chicken Dum Biryani", state.foods.first().name) // 4.8 is highest rating
    }

    @Test
    fun `test sorting by price ascending and descending`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        // Ascending
        viewModel.applySorting(SearchSort.PRICE_ASC)
        val stateAsc = viewModel.uiState.value
        assertEquals("Cheese Burst Burger", stateAsc.foods.first().name) // 189.0 is cheapest
        
        // Descending
        viewModel.applySorting(SearchSort.PRICE_DESC)
        val stateDesc = viewModel.uiState.value
        assertEquals("Chicken Dum Biryani", stateDesc.foods.first().name) // 299.0 is most expensive
    }

    @Test
    fun `test combined filters and sorting`() = runBlocking {
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))
        
        // Pizza + Veg + rating 4.0+ + Price < 300
        val combinedFilters = SearchFilters(
            category = "Pizza",
            isVegetarian = true,
            minRating = 4.0,
            maxPrice = 300.0
        )
        viewModel.applyFilters(combinedFilters)
        viewModel.applySorting(SearchSort.RATING_DESC)

        val state = viewModel.uiState.value
        assertEquals(1, state.foods.size)
        assertEquals("Margherita Pizza", state.foods.first().name)
    }

    @Test
    fun `test repository error state`() = runBlocking {
        mockRepository.shouldReturnError = true
        viewModel = SearchViewModel(mockRepository, CoroutineScope(Dispatchers.Unconfined))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Unable to load directory information.", state.error)
    }

    class MockSearchRepository : SearchRepository {
        var shouldReturnError = false
        private val recentSearchesFlow = MutableStateFlow<List<RecentSearchEntity>>(emptyList())

        override fun getRecentSearches(): Flow<List<RecentSearchEntity>> = recentSearchesFlow

        override suspend fun insertRecentSearch(query: String) {
            val currentList = recentSearchesFlow.value.toMutableList()
            // remove existing duplicate
            currentList.removeAll { it.query == query }
            currentList.add(0, RecentSearchEntity(query, System.currentTimeMillis()))
            recentSearchesFlow.value = currentList
        }

        override suspend fun deleteRecentSearch(query: String) {
            val currentList = recentSearchesFlow.value.toMutableList()
            currentList.removeAll { it.query == query }
            recentSearchesFlow.value = currentList
        }

        override suspend fun clearRecentSearches() {
            recentSearchesFlow.value = emptyList()
        }

        override suspend fun getCategories(): Resource<List<Category>> {
            return if (shouldReturnError) {
                Resource.Error("Categories error", null)
            } else {
                Resource.Success(listOf(
                    Category("c1", "Pizza"),
                    Category("c2", "Burger"),
                    Category("c3", "Biryani")
                ))
            }
        }

        override suspend fun getRestaurants(): Resource<List<Restaurant>> {
            return if (shouldReturnError) {
                Resource.Error("Restaurants error", null)
            } else {
                Resource.Success(listOf(
                    Restaurant("r1", "Pizza Palace", rating = 4.5, deliveryTime = "25 mins", categories = listOf("c1")),
                    Restaurant("r2", "Burger Bistro", rating = 4.2, deliveryTime = "15 mins", categories = listOf("c2")),
                    Restaurant("r3", "Biryani House", rating = 4.7, deliveryTime = "35 mins", categories = listOf("c3"))
                ))
            }
        }

        override suspend fun getFoods(): Resource<List<Food>> {
            return if (shouldReturnError) {
                Resource.Error("Foods error", null)
            } else {
                Resource.Success(listOf(
                    Food("f1", "r1", "c1", "Margherita Pizza", price = 249.0, rating = 4.6, isVegetarian = true),
                    Food("f2", "r2", "c2", "Cheese Burst Burger", price = 189.0, rating = 4.3, isVegetarian = false),
                    Food("f3", "r3", "c3", "Chicken Dum Biryani", price = 299.0, rating = 4.8, isVegetarian = false)
                ))
            }
        }
    }
}
