package com.foodfusionai.app.ui.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers
import com.foodfusionai.app.utils.Resource

/**
 * Base ViewModel for all ViewModels in the application.
 */
abstract class BaseViewModel : ViewModel() {

    protected val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    protected fun <T> performOperation(operation: suspend () -> T): LiveData<Resource<T>> {
        return liveData(Dispatchers.IO) {
            emit(Resource.Loading)
            try {
                val result = operation()
                emit(Resource.Success(result))
            } catch (e: Exception) {
                emit(Resource.Error(handleException(e), e))
            }
        }
    }

    protected fun handleException(e: Exception): String {
        val message = e.message ?: "An unexpected error occurred."
        _errorMessage.postValue(message)
        return message
    }
}
