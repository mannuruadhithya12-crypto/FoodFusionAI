package com.foodfusionai.app.ui.profile.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.models.Address
import com.foodfusionai.app.data.repository.AddressRepository
import com.foodfusionai.app.data.repository.AddressRepositoryImpl
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddressViewModel(
    private val addressRepository: AddressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddressUiState())
    val uiState: StateFlow<AddressUiState> = _uiState.asStateFlow()

    init {
        loadAddresses()
    }

    private fun loadAddresses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            addressRepository.observeAddresses().collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is Resource.Success -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                addresses = result.data ?: emptyList(),
                                isOffline = false,
                                error = null
                            ) 
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                error = if (it.addresses.isEmpty()) result.message else null,
                                isOffline = it.addresses.isNotEmpty()
                            ) 
                        }
                    }
                    is Resource.Empty -> {
                        _uiState.update { it.copy(isLoading = false, addresses = emptyList()) }
                    }
                }
            }
        }
    }

    fun saveAddress(
        id: String?,
        recipientName: String,
        phoneNumber: String,
        type: String,
        street: String,
        city: String,
        state: String,
        zipCode: String,
        landmark: String,
        instructions: String,
        // Phase 16: real coordinates from map picker (0.0 when not set)
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        geohash: String = "",
        placeId: String = ""
    ) {
        val errors = mutableMapOf<String, String>()
        
        if (recipientName.isBlank()) errors["recipientName"] = "Required"
        if (phoneNumber.isBlank() || !phoneNumber.matches(Regex("^[0-9]{10}$"))) errors["phoneNumber"] = "Valid 10-digit number required"
        if (street.isBlank()) errors["street"] = "Required"
        if (city.isBlank()) errors["city"] = "Required"
        if (state.isBlank()) errors["state"] = "Required"
        if (zipCode.isBlank() || !zipCode.matches(Regex("^[0-9]{6}$"))) errors["zipCode"] = "Valid 6-digit code required"

        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationError = errors) }
            return
        }

        _uiState.update { it.copy(isLoading = true, validationError = emptyMap(), error = null, isSuccess = false) }

        viewModelScope.launch {
            val address = Address(
                id = id ?: "",
                recipientName = recipientName.trim(),
                phoneNumber = phoneNumber.trim(),
                type = type,
                street = street.trim(),
                city = city.trim(),
                state = state.trim(),
                zipCode = zipCode.trim(),
                landmark = landmark.trim(),
                instructions = instructions.trim(),
                // Phase 16: real coordinates
                latitude = latitude,
                longitude = longitude,
                geohash = geohash,
                placeId = placeId
            )

            val result = if (id.isNullOrEmpty()) {
                addressRepository.addAddress(address)
            } else {
                addressRepository.updateAddress(address)
            }

            if (result is Resource.Success) {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } else if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
            }
        }
    }

    fun deleteAddress(addressId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = addressRepository.deleteAddress(addressId)
            
            if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setDefaultAddress(addressId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = addressRepository.setDefaultAddress(addressId)
            
            if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun resetState() {
        _uiState.update { it.copy(error = null, isSuccess = false, validationError = emptyMap()) }
    }

    class Factory(private val repository: AddressRepository = AddressRepositoryImpl()) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AddressViewModel::class.java)) {
                return AddressViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
