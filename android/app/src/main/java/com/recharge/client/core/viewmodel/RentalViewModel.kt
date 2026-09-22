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
    val bookings: List<RentalBookingResponse> = emptyList(),
    val payouts: List<RentalVendorPayoutResponse> = emptyList(),
    val vehicleUnavailabilityByCar: Map<String, List<RentalVehicleUnavailabilityResponse>> = emptyMap(),
    val vehicleCalendar: RentalVehicleCalendarResponse? = null,
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

    fun clearCarSearch() {
        _state.value = _state.value.copy(cars = emptyList(), error = null)
    }

    fun loadCars(startDate: String? = null, endDate: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.rentalCars(startDate, endDate)
                .onSuccess { _state.value = _state.value.copy(cars = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Unable to load rental cars") }
        }
    }


    fun loadBookings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.rentalBookings()
                .onSuccess { response -> _state.value = _state.value.copy(bookings = response.items, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Unable to load rental bookings") }
        }
    }

    fun cancelBooking(bookingId: String, onDone: () -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.cancelRentalBooking(bookingId)
                .onSuccess { cancelled ->
                    _state.value = _state.value.copy(
                        bookings = _state.value.bookings.map { if (it.bookingId == cancelled.bookingId) cancelled else it },
                        saving = false
                    )
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "Unable to cancel booking") }
        }
    }

    fun createBooking(request: RentalBookingRequest, onDone: () -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.createRentalBooking(request)
                .onSuccess { booking -> _state.value = _state.value.copy(bookings = listOf(booking) + _state.value.bookings.filterNot { it.bookingId == booking.bookingId }, saving = false); onDone() }
                .onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "Unable to create booking") }
        }
    }

    fun quoteBooking(request: RentalBookingQuoteRequest, onDone: (RentalBookingQuoteResponse) -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.rentalBookingQuote(request)
                .onSuccess { _state.value = _state.value.copy(saving = false); onDone(it) }
                .onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "Unable to calculate rental quote") }
        }
    }


    fun loadVendorPayouts() {
        viewModelScope.launch {
            repository.rentalVendorPayouts()
                .onSuccess { _state.value = _state.value.copy(payouts = it) }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "Unable to load vendor payouts") }
        }
    }
    fun loadVendorVehicles() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.rentalVendorVehicles()
                .onSuccess { _state.value = _state.value.copy(vendorCars = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Unable to load vendor vehicles") }
        }
    }

    fun takeVehicleOffMarket(
        carId: String,
        request: RentalVehicleUnavailabilityRequest,
        onDone: () -> Unit
    ) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.takeRentalVehicleOffMarket(carId, request)
                .onSuccess { created ->
                    val current = _state.value.vehicleUnavailabilityByCar[carId].orEmpty()
                    _state.value = _state.value.copy(
                        vehicleUnavailabilityByCar = _state.value.vehicleUnavailabilityByCar + (carId to (current + created).sortedBy { it.startDate }),
                        saving = false
                    )
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "Unable to take vehicle off market") }
        }
    }

    fun loadVehicleUnavailability(carId: String) {
        viewModelScope.launch {
            repository.rentalVehicleUnavailability(carId)
                .onSuccess { rows ->
                    _state.value = _state.value.copy(
                        vehicleUnavailabilityByCar = _state.value.vehicleUnavailabilityByCar + (carId to rows)
                    )
                }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "Unable to load vehicle availability") }
        }
    }

    fun restoreVehicleToMarket(carId: String, unavailableId: String, onDone: () -> Unit = {}) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.restoreRentalVehicleToMarket(carId, unavailableId)
                .onSuccess {
                    val rows = _state.value.vehicleUnavailabilityByCar[carId].orEmpty()
                        .filterNot { it.id == unavailableId }
                    _state.value = _state.value.copy(
                        vehicleUnavailabilityByCar = _state.value.vehicleUnavailabilityByCar + (carId to rows),
                        saving = false
                    )
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "Unable to restore vehicle to market") }
        }
    }

    fun loadVehicleCalendar(carId: String, year: Int, month: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            repository.rentalVehicleCalendar(carId, year, month)
                .onSuccess { _state.value = _state.value.copy(vehicleCalendar = it) }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "Unable to load vehicle calendar") }
        }
    }

    fun resubmitVehicle(carId: String, request: RentalVehicleUpdateRequest, onDone: () -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.resubmitRentalVehicle(carId, request)
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        vendorCars = _state.value.vendorCars.map { if (it.id == updated.id) updated else it },
                        saving = false
                    )
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "Unable to resubmit vehicle") }
        }
    }

    fun onboardVehicle(request: RentalVehicleOnboardingRequest, onDone: () -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.onboardRentalVehicle(request)
                .onSuccess { _state.value = _state.value.copy(vendorCars = _state.value.vendorCars + it, saving = false); onDone() }
                .onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "Unable to submit vehicle") }
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
