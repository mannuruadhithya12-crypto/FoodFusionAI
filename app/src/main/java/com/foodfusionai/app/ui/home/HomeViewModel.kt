package com.foodfusionai.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.models.Category
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.data.models.Offer
import com.foodfusionai.app.data.models.Restaurant
import com.foodfusionai.app.data.models.User
import com.foodfusionai.app.data.repository.AuthRepository
import com.foodfusionai.app.data.repository.AuthRepositoryImpl
import com.foodfusionai.app.data.repository.HomeRepository
import com.foodfusionai.app.data.repository.HomeRepositoryImpl
import com.foodfusionai.app.data.repository.LocationRepository
import com.foodfusionai.app.data.repository.LocationRepositoryImpl
import com.foodfusionai.app.data.repository.NotificationRepository
import com.foodfusionai.app.data.repository.NotificationRepositoryImpl
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Coherent UI state for the Home screen.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUser: User? = null,
    val banners: List<Offer> = emptyList(),
    val categories: List<Category> = emptyList(),
    val restaurants: List<Restaurant> = emptyList(),
    val trendingFoods: List<Food> = emptyList(),
    val recommendedFoods: List<com.foodfusionai.app.data.models.RecommendationItem> = emptyList(),
    val topRatedRestaurants: List<Restaurant> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val currentLocationAddress: String? = null
)

/**
 * ViewModel managing Home screen data and business state.
 * Uses Kotlin Coroutines to execute calls concurrently and map states.
 */
class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val locationRepository: LocationRepository,
    private val recommendationRepository: com.foodfusionai.app.data.repository.RecommendationRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeCurrentUser()
        observeNotifications()
        loadHomeData()
    }

    fun fetchCurrentLocation() {
        scope.launch {
            val locationRes = locationRepository.getCurrentLocation()
            if (locationRes is Resource.Success) {
                val coords = locationRes.data!!
                val addressRes = locationRepository.getAddressFromCoordinates(coords.first, coords.second)
                if (addressRes is Resource.Success) {
                    _uiState.update { it.copy(currentLocationAddress = addressRes.data) }
                }
            }
        }
    }

    private fun observeNotifications() {
        scope.launch {
            notificationRepository.observeNotifications().collect { resource ->
                if (resource is Resource.Success) {
                    val count = resource.data?.count { !it.isRead } ?: 0
                    _uiState.update { it.copy(unreadNotificationCount = count) }
                }
            }
        }
    }

    private fun observeCurrentUser() {
        scope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    fun loadHomeData() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Fetch data elements concurrently (using launch/async is also possible, simple sequential is fine for offline cache/coroutines)
            val bannersResult = homeRepository.getOffers()
            val categoriesResult = homeRepository.getCategories()
            val restaurantsResult = homeRepository.getRestaurants()
            val foodsResult = homeRepository.getTrendingFoods()
            val recommendationsResult = recommendationRepository.getPersonalizedRecommendations()

            val hasError = bannersResult is Resource.Error ||
                    categoriesResult is Resource.Error ||
                    restaurantsResult is Resource.Error ||
                    foodsResult is Resource.Error

            if (hasError) {
                // If there's an error but we have partially returned cache list, we can show it,
                // otherwise show a friendly error status message.
                val errorMessage = when {
                    bannersResult is Resource.Error -> bannersResult.message
                    categoriesResult is Resource.Error -> categoriesResult.message
                    restaurantsResult is Resource.Error -> restaurantsResult.message
                    foodsResult is Resource.Error -> foodsResult.message
                    else -> "Failed to load menu. Please check your internet connection."
                }
                _uiState.update { it.copy(isLoading = false, error = errorMessage) }
                return@launch
            }

            val banners = (bannersResult as? Resource.Success)?.data ?: emptyList()
            val categories = (categoriesResult as? Resource.Success)?.data ?: emptyList()
            val restaurants = (restaurantsResult as? Resource.Success)?.data ?: emptyList()
            val foods = (foodsResult as? Resource.Success)?.data ?: emptyList()
            val recommended = (recommendationsResult as? Resource.Success)?.data ?: emptyList()

            // Top rated restaurants (rating >= 4.5)
            val topRated = restaurants.filter { it.rating >= 4.5 }.sortedByDescending { it.rating }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    banners = banners,
                    categories = categories,
                    restaurants = restaurants,
                    trendingFoods = foods,
                    recommendedFoods = recommended,
                    topRatedRestaurants = topRated
                )
            }
        }
    }

    /**
     * Factory class for instantiating HomeViewModel.
     */
    class Factory(
        private val homeRepository: HomeRepository = HomeRepositoryImpl(),
        private val authRepository: AuthRepository = AuthRepositoryImpl(),
        private val notificationRepository: NotificationRepository = NotificationRepositoryImpl(),
        private val locationRepository: LocationRepository,
        private val recommendationRepository: com.foodfusionai.app.data.repository.RecommendationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(homeRepository, authRepository, notificationRepository, locationRepository, recommendationRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
