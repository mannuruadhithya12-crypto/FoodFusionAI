package com.foodfusionai.app.ui.profile.address

import com.foodfusionai.app.data.models.Address

data class AddressUiState(
    val addresses: List<Address> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val validationError: Map<String, String> = emptyMap(),
    val isSuccess: Boolean = false,
    val isOffline: Boolean = false
)
