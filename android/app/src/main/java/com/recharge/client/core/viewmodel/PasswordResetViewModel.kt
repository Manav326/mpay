package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PasswordResetUiState {
    data object Idle : PasswordResetUiState
    data object Sending : PasswordResetUiState
    data class OtpSent(val expiresInSeconds: Long, val demoOtp: String? = null, val deliveryMode: String = "twilio") : PasswordResetUiState
    data object Resetting : PasswordResetUiState
    data object Success : PasswordResetUiState
    data class Error(val message: String) : PasswordResetUiState
}

class PasswordResetViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository(application)
    private val _state = MutableStateFlow<PasswordResetUiState>(PasswordResetUiState.Idle)
    val state = _state.asStateFlow()

    fun requestOtp(mobile: String) {
        viewModelScope.launch {
            _state.value = PasswordResetUiState.Sending
            _state.value = repository.forgotPassword(mobile).fold(
                onSuccess = { PasswordResetUiState.OtpSent(it.expiresInSeconds, it.demoOtp, it.deliveryMode) },
                onFailure = { PasswordResetUiState.Error(it.message ?: "Unable to send OTP") }
            )
        }
    }

    fun resetPassword(mobile: String, otp: String, newPassword: String) {
        viewModelScope.launch {
            _state.value = PasswordResetUiState.Resetting
            _state.value = repository.resetPassword(mobile, otp, newPassword).fold(
                onSuccess = { PasswordResetUiState.Success },
                onFailure = { PasswordResetUiState.Error(it.message ?: "Unable to reset password") }
            )
        }
    }

    fun clear() { _state.value = PasswordResetUiState.Idle }
}
