package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.*
import com.recharge.client.core.repository.ClientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RentalUiState(
    val vendor: RentalVendorResponse? = null,
    val cars: List<RentalCarResponse> = emptyList(),
    val vendorCars: List<RentalCarResponse> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

class RentalViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository(application)
    private val _state = MutableStateFlow(RentalUiState())
    val state = _state.asStateFlow()

    fun loadVendor() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.rentalVendor()
                .onSuccess { _state.value = _state.value.copy(vendor = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Unable to load rental vendor profile") }
        }
    }

    fun loadCars() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.rentalCars()
                .onSuccess { _state.value = _state.value.copy(cars = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Unable to load rental cars") }
        }
    }

    fun onboardVendor(request: RentalVendorOnboardingRequest, onDone: () -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.onboardRentalVendor(request)
                .onSuccess { _state.value = _state.value.copy(vendor = it, saving = false); onDone() }
                .onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "Unable to submit vendor onboarding") }
        }
    }
}
