package com.recharge.client.core.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.CurrentUserResponse
import com.recharge.client.core.repository.ClientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: CurrentUserResponse? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val deletingImage: Boolean = false,
    val deletingAccount: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository.getInstance(application)
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    fun resetSession() {
        viewModelScope.coroutineContext.cancelChildren()
        _state.value = ProfileUiState()
    }

    fun load() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, saved = false)
            repository.profile()
                .onSuccess { user -> _state.value = ProfileUiState(user = user) }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to load profile.") }
        }
    }

    fun save(name: String, email: String, imageUri: Uri?) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null, saved = false)
            var latest: CurrentUserResponse? = null
            repository.updateProfile(name.trim().takeIf { it.isNotBlank() }, email.trim().takeIf { it.isNotBlank() })
                .onSuccess { latest = it }
                .onFailure { e ->
                    _state.value = _state.value.copy(saving = false, error = e.message ?: "Unable to update profile.")
                    return@launch
                }

            if (imageUri != null) {
                repository.uploadProfileImage(imageUri)
                    .onSuccess { latest = it }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            user = latest ?: _state.value.user,
                            saving = false,
                            saved = true,
                            error = e.message ?: "Profile saved, but the photo could not be uploaded."
                        )
                        return@launch
                    }
            }

            _state.value = ProfileUiState(user = latest ?: _state.value.user, saved = true)
        }
    }

    fun deleteAccount(password: String, confirmation: String, onDeleted: () -> Unit) {
        if (_state.value.deletingAccount) return
        viewModelScope.launch {
            _state.value = _state.value.copy(deletingAccount = true, error = null)
            repository.deleteAccount(password, confirmation)
                .onSuccess {
                    _state.value = ProfileUiState()
                    onDeleted()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        deletingAccount = false,
                        error = e.message ?: "Unable to delete your account."
                    )
                }
        }
    }

    fun removePhoto() {
        if (_state.value.deletingImage) return
        viewModelScope.launch {
            _state.value = _state.value.copy(deletingImage = true, error = null, saved = false)
            repository.deleteProfileImage()
                .onSuccess { _state.value = _state.value.copy(user = it, deletingImage = false, saved = true) }
                .onFailure { e -> _state.value = _state.value.copy(deletingImage = false, error = e.message ?: "Unable to remove profile photo.") }
        }
    }
}
