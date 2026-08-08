package com.foodfusionai.app.ui.profile

import com.foodfusionai.app.data.models.User

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false,
    val isAccountDeleted: Boolean = false,
    val isOffline: Boolean = false
)
