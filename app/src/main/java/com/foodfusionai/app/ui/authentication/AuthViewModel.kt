package com.foodfusionai.app.ui.authentication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foodfusionai.app.data.models.User
import com.foodfusionai.app.data.repository.AuthRepository
import com.foodfusionai.app.data.repository.AuthRepositoryImpl
import com.foodfusionai.app.utils.Resource
import com.foodfusionai.app.utils.isValidEmail
import com.foodfusionai.app.utils.isValidPassword
import com.foodfusionai.app.utils.isValidPhone
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _loginState = MutableLiveData<Resource<User>?>()
    val loginState: LiveData<Resource<User>?> = _loginState

    private val _registerState = MutableLiveData<Resource<User>?>()
    val registerState: LiveData<Resource<User>?> = _registerState

    private val _forgotPasswordState = MutableLiveData<Resource<Unit>?>()
    val forgotPasswordState: LiveData<Resource<Unit>?> = _forgotPasswordState

    private val _emailError = MutableLiveData<String?>()
    val emailError: LiveData<String?> = _emailError

    private val _passwordError = MutableLiveData<String?>()
    val passwordError: LiveData<String?> = _passwordError

    private val _nameError = MutableLiveData<String?>()
    val nameError: LiveData<String?> = _nameError

    private val _phoneError = MutableLiveData<String?>()
    val phoneError: LiveData<String?> = _phoneError

    private val _confirmPasswordError = MutableLiveData<String?>()
    val confirmPasswordError: LiveData<String?> = _confirmPasswordError

    fun validateLogin(email: String, password: String): Boolean {
        var isValid = true
        if (email.isBlank() || !email.isValidEmail()) {
            _emailError.value = "Please enter a valid email address"
            isValid = false
        } else {
            _emailError.value = null
        }

        if (password.isBlank()) {
            _passwordError.value = "Please enter your password"
            isValid = false
        } else {
            _passwordError.value = null
        }
        return isValid
    }

    fun validateRegister(name: String, email: String, phone: String, password: String, confirmPassword: String): Boolean {
        var isValid = true
        if (name.isBlank()) {
            _nameError.value = "Please enter your name"
            isValid = false
        } else if (name.length > 50) {
            _nameError.value = "Name cannot exceed 50 characters"
            isValid = false
        } else {
            _nameError.value = null
        }

        if (email.isBlank() || !email.isValidEmail()) {
            _emailError.value = "Please enter a valid email address"
            isValid = false
        } else {
            _emailError.value = null
        }

        if (phone.isBlank() || !phone.isValidPhone()) {
            _phoneError.value = "Please enter a valid 10-digit phone number"
            isValid = false
        } else {
            _phoneError.value = null
        }

        if (password.isBlank() || !password.isValidPassword()) {
            _passwordError.value = "Password must be at least 8 characters, with 1 uppercase, 1 lowercase, 1 digit, and 1 special character"
            isValid = false
        } else {
            _passwordError.value = null
        }

        if (confirmPassword != password) {
            _confirmPasswordError.value = "Passwords do not match"
            isValid = false
        } else {
            _confirmPasswordError.value = null
        }

        return isValid
    }

    fun validateForgotPassword(email: String): Boolean {
        if (email.isBlank() || !email.isValidEmail()) {
            _emailError.value = "Please enter a valid email address"
            return false
        }
        _emailError.value = null
        return true
    }

    fun login(email: String, password: String) {
        if (!validateLogin(email, password)) return
        viewModelScope.launch {
            _loginState.value = Resource.Loading
            _loginState.value = repository.login(email, password)
        }
    }

    fun register(name: String, email: String, phone: String, password: String, confirmPassword: String) {
        if (!validateRegister(name, email, phone, password, confirmPassword)) return
        viewModelScope.launch {
            _registerState.value = Resource.Loading
            _registerState.value = repository.register(name, email, phone, password)
        }
    }

    fun sendPasswordResetEmail(email: String) {
        if (!validateForgotPassword(email)) return
        viewModelScope.launch {
            _forgotPasswordState.value = Resource.Loading
            _forgotPasswordState.value = repository.sendPasswordResetEmail(email)
        }
    }

    fun isLoggedIn(): Boolean {
        return repository.isLoggedIn()
    }

    fun resetStates() {
        _loginState.value = null
        _registerState.value = null
        _forgotPasswordState.value = null
    }

    class Factory(private val repository: AuthRepository = AuthRepositoryImpl()) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                return AuthViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
