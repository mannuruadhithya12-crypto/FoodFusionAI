package com.foodfusionai.app.ui.reviews

import com.foodfusionai.app.data.models.Review

data class ReviewUiState(
    val reviews: List<Review> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val currentTargetId: String = ""
)
