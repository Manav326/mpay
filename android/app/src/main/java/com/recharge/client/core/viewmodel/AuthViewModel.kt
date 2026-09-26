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

sealed interface RegistrationOtpUiState {
    data object Idle : RegistrationOtpUiState
    data object Sending : RegistrationOtpUiState
    data class Sent(
        val expiresInSeconds: Long,
        val resendAfterSeconds: Long,
        val maskedMobile: String,
        val demoOtp: String? = null,
        val deliveryMode: String = "way2api"
    ) : RegistrationOtpUiState
    data object Verifying : RegistrationOtpUiState
    data class Verified(val verificationToken: String) : RegistrationOtpUiState
    data class Error(val message: String) : RegistrationOtpUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository.getInstance(application)
    private val _state = MutableStateFlow<AuthUiState>(
        if (repository.isLoggedIn()) AuthUiState.Authenticated else AuthUiState.Idle
    )
    val state = _state.asStateFlow()
    private val _registrationOtpState = MutableStateFlow<RegistrationOtpUiState>(RegistrationOtpUiState.Idle)
    val registrationOtpState = _registrationOtpState.asStateFlow()

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

    fun register(name: String, email: String, mobile: String, password: String, mobileVerificationToken: String? = null) {
        if (_state.value is AuthUiState.Loading) return
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = repository.register(name, email, mobile, password, mobileVerificationToken)
            _state.value = result.fold(
                onSuccess = { AuthUiState.Authenticated },
                onFailure = { AuthUiState.Error(it.message ?: "Registration failed") }
            )
        }
    }

    fun sendRegistrationOtp(mobile: String) {
        if (_registrationOtpState.value is RegistrationOtpUiState.Sending ||
            _registrationOtpState.value is RegistrationOtpUiState.Verifying) return
        _registrationOtpState.value = RegistrationOtpUiState.Sending
        viewModelScope.launch {
            _registrationOtpState.value = repository.sendRegistrationOtp(mobile).fold(
                onSuccess = {
                    RegistrationOtpUiState.Sent(
                        expiresInSeconds = it.expiresInSeconds,
                        resendAfterSeconds = it.resendAfterSeconds,
                        maskedMobile = it.maskedMobile,
                        demoOtp = it.demoOtp,
                        deliveryMode = it.deliveryMode
                    )
                },
                onFailure = { RegistrationOtpUiState.Error(it.message ?: "Unable to send OTP") }
            )
        }
    }

    fun verifyRegistrationOtp(mobile: String, otp: String) {
        if (_registrationOtpState.value is RegistrationOtpUiState.Verifying) return
        _registrationOtpState.value = RegistrationOtpUiState.Verifying
        viewModelScope.launch {
            _registrationOtpState.value = repository.verifyRegistrationOtp(mobile, otp).fold(
                onSuccess = {
                    val token = it.verificationToken
                        ?: return@fold RegistrationOtpUiState.Error("Mobile verification could not be completed")
                    RegistrationOtpUiState.Verified(token)
                },
                onFailure = { RegistrationOtpUiState.Error(it.message ?: "Invalid OTP") }
            )
        }
    }

    fun clearRegistrationOtp() {
        _registrationOtpState.value = RegistrationOtpUiState.Idle
    }

    fun clearError() {
        if (_state.value is AuthUiState.Error) _state.value = AuthUiState.Idle
    }

    fun logout() {
        viewModelScope.coroutineContext.cancelChildren()
        repository.logout()
        ProfileCacheStore(getApplication()).clear()
        _registrationOtpState.value = RegistrationOtpUiState.Idle
        _state.value = AuthUiState.Idle
    }
}
