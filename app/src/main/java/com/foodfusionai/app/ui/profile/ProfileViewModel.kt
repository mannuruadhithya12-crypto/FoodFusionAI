package com.foodfusionai.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.repository.ProfileRepository
import com.foodfusionai.app.data.repository.ProfileRepositoryImpl
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            profileRepository.observeProfile().collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is Resource.Success -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                user = result.data,
                                isOffline = false,
                                error = null
                            ) 
                        }
                    }
                    is Resource.Error -> {
                        // If we have an existing user, we might be offline
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                error = if (it.user == null) result.message else null,
                                isOffline = it.user != null
                            ) 
                        }
                    }
                    is Resource.Empty -> {
                        _uiState.update { it.copy(isLoading = false, error = "Profile not found") }
                    }
                }
            }
        }
    }

    fun updateProfile(displayName: String, phoneNumber: String) {
        if (displayName.isBlank() || phoneNumber.isBlank()) {
            _uiState.update { it.copy(error = "Fields cannot be empty") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = profileRepository.updateProfile(displayName.trim(), phoneNumber.trim())
            
            if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = profileRepository.logout()
            
            if (result is Resource.Success) {
                _uiState.update { it.copy(isLoading = false, isLoggedOut = true) }
            } else if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = profileRepository.deleteAccount()
            
            if (result is Resource.Success) {
                _uiState.update { it.copy(isLoading = false, isAccountDeleted = true) }
            } else if (result is Resource.Error) {
                val errorMsg = result.message
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
            }
        }
    }
    
    fun resetError() {
        _uiState.update { it.copy(error = null) }
    }

    class Factory(private val repository: ProfileRepository = ProfileRepositoryImpl()) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                return ProfileViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
