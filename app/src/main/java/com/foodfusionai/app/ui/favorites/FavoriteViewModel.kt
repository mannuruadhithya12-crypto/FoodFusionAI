package com.foodfusionai.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.repository.FavoriteRepository
import com.foodfusionai.app.data.repository.FavoriteRepositoryImpl
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FavoriteViewModel(
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoriteUiState())
    val uiState: StateFlow<FavoriteUiState> = _uiState.asStateFlow()

    init {
        observeFavorites()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoriteRepository.observeFavorites().collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true, error = null) }
                    }
                    is Resource.Success -> {
                        _uiState.update { it.copy(isLoading = false, favorites = result.data, error = null) }
                    }
                    is Resource.Error -> {
                        val errorMsg = result.message
                        _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                    }
                    else -> {}
                }
            }
        }
    }

    fun toggleFavorite(targetId: String, targetType: String, targetName: String, imageUrl: String, restaurantId: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val result = favoriteRepository.toggleFavorite(targetId, targetType, targetName, imageUrl, restaurantId)
            
            if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(isUpdating = false, error = errorMsg) }
            } else {
                _uiState.update { it.copy(isUpdating = false) }
            }
        }
    }

    fun resetError() {
        _uiState.update { it.copy(error = null) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FavoriteViewModel::class.java)) {
                return FavoriteViewModel(FavoriteRepositoryImpl()) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
