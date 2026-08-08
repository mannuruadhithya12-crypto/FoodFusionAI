package com.foodfusionai.app.ui.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.repository.ReviewRepository
import com.foodfusionai.app.data.repository.ReviewRepositoryImpl
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReviewViewModel(
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    fun loadReviews(targetId: String) {
        _uiState.update { it.copy(currentTargetId = targetId) }
        viewModelScope.launch {
            reviewRepository.getReviewsForTarget(targetId).collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true, error = null) }
                    }
                    is Resource.Success -> {
                        _uiState.update { it.copy(isLoading = false, reviews = result.data, error = null) }
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

    fun submitReview(orderId: String, restaurantId: String, foodId: String?, rating: Int, comment: String, userName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, isSuccess = false) }
            val result = reviewRepository.createReview(orderId, restaurantId, foodId, rating, comment, userName)
            
            if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(isSubmitting = false, error = errorMsg) }
            } else {
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            }
        }
    }

    fun editReview(reviewId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, isSuccess = false) }
            val result = reviewRepository.editReview(reviewId, rating, comment)
            
            if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(isSubmitting = false, error = errorMsg) }
            } else {
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            }
        }
    }

    fun deleteReview(reviewId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = reviewRepository.deleteReview(reviewId)
            
            if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
            } else {
                _uiState.update { it.copy(isLoading = false) } // UI flow will refresh via flow collector
            }
        }
    }

    fun interactReview(reviewId: String, action: String, reason: String? = null) {
        viewModelScope.launch {
            val result = reviewRepository.interactReview(reviewId, action, reason)
            if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(error = errorMsg) }
            }
        }
    }

    fun resetState() {
        _uiState.update { it.copy(error = null, isSuccess = false) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReviewViewModel::class.java)) {
                return ReviewViewModel(ReviewRepositoryImpl()) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
