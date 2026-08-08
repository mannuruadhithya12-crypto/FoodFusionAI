package com.foodfusionai.app.ui.home

import com.foodfusionai.app.data.models.*
import com.foodfusionai.app.data.repository.*
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

    private lateinit var mockHomeRepository: MockHomeRepository
    private lateinit var mockAuthRepository: MockAuthRepository
    private lateinit var mockNotificationRepository: MockNotificationRepository
    private lateinit var mockLocationRepository: MockLocationRepository
    private lateinit var mockRecommendationRepository: MockRecommendationRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        mockHomeRepository = MockHomeRepository()
        mockAuthRepository = MockAuthRepository()
        mockNotificationRepository = MockNotificationRepository()
        mockLocationRepository = MockLocationRepository()
        mockRecommendationRepository = MockRecommendationRepository()
    }

    @Test
    fun `test load home data success`() = runBlocking {
        viewModel = HomeViewModel(mockHomeRepository, mockAuthRepository, mockNotificationRepository, mockLocationRepository, mockRecommendationRepository, CoroutineScope(Dispatchers.Unconfined))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(3, state.banners.size)
        assertEquals(2, state.categories.size)
        assertEquals(2, state.restaurants.size)
        assertEquals(2, state.trendingFoods.size)
        
        // Recommended foods
        assertEquals("f1", state.recommendedFoods.first().food.id)
        
        // Top rated restaurants (rating >= 4.5)
        assertEquals(1, state.topRatedRestaurants.size)
        assertEquals("r2", state.topRatedRestaurants.first().id) // rating 4.8 >= 4.5
    }

    @Test
    fun `test load home data error`() = runBlocking {
        mockHomeRepository.shouldReturnError = true
        viewModel = HomeViewModel(mockHomeRepository, mockAuthRepository, mockNotificationRepository, mockLocationRepository, mockRecommendationRepository, CoroutineScope(Dispatchers.Unconfined))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Failed to load offers", state.error)
        assertEquals(emptyList<Offer>(), state.banners)
    }

    @Test
    fun `test observe current authenticated user`() = runBlocking {
        val mockUser = User("u123", "test@foodfusion.ai", "Chef Mario")
        mockAuthRepository.currentUserFlow.value = mockUser

        viewModel = HomeViewModel(mockHomeRepository, mockAuthRepository, mockNotificationRepository, mockLocationRepository, mockRecommendationRepository, CoroutineScope(Dispatchers.Unconfined))

        val state = viewModel.uiState.value
        assertNotNull(state.currentUser)
        assertEquals("Chef Mario", state.currentUser?.displayName)
    }

    class MockHomeRepository : HomeRepository {
        var shouldReturnError = false

        override suspend fun getOffers(): Resource<List<Offer>> {
            return if (shouldReturnError) {
                Resource.Error("Failed to load offers", null)
            } else {
                Resource.Success(listOf(
                    Offer("o1", "Offer 1"),
                    Offer("o2", "Offer 2"),
                    Offer("o3", "Offer 3")
                ))
            }
        }

        override suspend fun getCategories(): Resource<List<Category>> {
            return if (shouldReturnError) {
                Resource.Error("Failed to load categories", null)
            } else {
                Resource.Success(listOf(
                    Category("c1", "Pizza"),
                    Category("c2", "Burger")
                ))
            }
        }

        override suspend fun getRestaurants(): Resource<List<Restaurant>> {
            return if (shouldReturnError) {
                Resource.Error("Failed to load restaurants", null)
            } else {
                Resource.Success(listOf(
                    Restaurant("r1", "Restaurant 1", rating = 4.2),
                    Restaurant("r2", "Restaurant 2", rating = 4.8)
                ))
            }
        }

        override suspend fun getTrendingFoods(): Resource<List<Food>> {
            return if (shouldReturnError) {
                Resource.Error("Failed to load trending foods", null)
            } else {
                Resource.Success(listOf(
                    Food("f1", "r1", "c1", "Pizza Margherita", rating = 4.5),
                    Food("f2", "r1", "c1", "Pizza Pepperoni", rating = 4.9)
                ))
            }
        }
    }

    class MockAuthRepository : AuthRepository {
        val currentUserFlow = MutableStateFlow<User?>(null)
        override val currentUser: Flow<User?> = currentUserFlow

        override fun isLoggedIn(): Boolean = currentUserFlow.value != null

        override suspend fun login(email: String, password: String): Resource<User> = Resource.Success(User())
        override suspend fun register(name: String, email: String, phone: String, password: String): Resource<User> = Resource.Success(User())
        override suspend fun sendPasswordResetEmail(email: String): Resource<Unit> = Resource.Success(Unit)
        override suspend fun logout(): Resource<Unit> = Resource.Success(Unit)
    }

    class MockNotificationRepository : NotificationRepository {
        override fun observeNotifications(): Flow<Resource<List<Notification>>> = emptyFlow()
        override suspend fun markAsRead(notificationId: String): Resource<Unit> = Resource.Success(Unit)
        override suspend fun markAllAsRead(): Resource<Unit> = Resource.Success(Unit)
        override suspend fun deleteNotification(notificationId: String): Resource<Unit> = Resource.Success(Unit)
    }

    class MockLocationRepository : LocationRepository {
        override suspend fun getCurrentLocation(): Resource<Pair<Double, Double>> = Resource.Success(Pair(0.0, 0.0))
        override suspend fun getAddressFromCoordinates(latitude: Double, longitude: Double): Resource<String> = Resource.Success("123 Main St")
    }

    class MockRecommendationRepository : RecommendationRepository {
        override suspend fun getPersonalizedRecommendations(forceRefresh: Boolean): Resource<List<RecommendationItem>> = Resource.Success(listOf(
            RecommendationItem(Food("f1", "r1", "c1", "Pizza Margherita", rating = 4.5), RecommendationReason.POPULAR_IN_AREA),
            RecommendationItem(Food("f2", "r1", "c1", "Pizza Pepperoni", rating = 4.9), RecommendationReason.TRENDING)
        ))
    }
}
