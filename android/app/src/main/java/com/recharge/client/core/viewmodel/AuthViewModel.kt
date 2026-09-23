package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.repository.AuthRepository
import com.recharge.client.core.cache.ProfileCacheStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Authenticated : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository.getInstance(application)
    private val _state = MutableStateFlow<AuthUiState>(
        if (repository.isLoggedIn()) AuthUiState.Authenticated else AuthUiState.Idle
    )
    val state = _state.asStateFlow()

    fun login(mobile: String, password: String) {
        if (_state.value is AuthUiState.Loading) return
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = repository.login(mobile, password)
            _state.value = result.fold(
                onSuccess = { AuthUiState.Authenticated },
                onFailure = { AuthUiState.Error(it.message ?: "Login failed") }
            )
        }
    }

    fun register(name: String, email: String, mobile: String, password: String) {
        if (_state.value is AuthUiState.Loading) return
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = repository.register(name, email, mobile, password)
            _state.value = result.fold(
                onSuccess = { AuthUiState.Authenticated },
                onFailure = { AuthUiState.Error(it.message ?: "Registration failed") }
            )
        }
    }

    fun clearError() {
        if (_state.value is AuthUiState.Error) _state.value = AuthUiState.Idle
    }

    fun logout() {
        viewModelScope.coroutineContext.cancelChildren()
        repository.logout()
        ProfileCacheStore(getApplication()).clear()
        _state.value = AuthUiState.Idle
    }
}
