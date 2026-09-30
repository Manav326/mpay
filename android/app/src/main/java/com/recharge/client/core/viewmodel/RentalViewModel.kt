package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.*
import com.recharge.client.core.repository.ClientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

data class RentalUiState(
    val vendor: RentalVendorResponse? = null,
    val cars: List<RentalCarResponse> = emptyList(),
    val vendorCars: List<RentalCarResponse> = emptyList(),
    val bookings: List<RentalBookingResponse> = emptyList(),
    val payouts: List<RentalVendorPayoutResponse> = emptyList(),
    val earnings: RentalVendorEarningsResponse? = null,
    val vehicleUnavailabilityByCar: Map<String, List<RentalVehicleUnavailabilityResponse>> = emptyMap(),
    val vehicleCalendar: RentalVehicleCalendarResponse? = null,
    val vendorLoading: Boolean = false,
    val marketplaceLoading: Boolean = false,
    val bookingsLoading: Boolean = false,
    val vendorVehiclesLoading: Boolean = false,
    val payoutsLoading: Boolean = false,
    val calendarLoading: Boolean = false,
    val saving: Boolean = false,
    val vendorError: String? = null,
    val marketplaceError: String? = null,
    val bookingsError: String? = null,
    val vendorVehiclesError: String? = null,
    val payoutsError: String? = null,
    val calendarError: String? = null,
    val availabilityError: String? = null,
    val mutationError: String? = null
)

class RentalViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository.getInstance(application)
    private val _state = MutableStateFlow(RentalUiState())
    private var carsJob: Job? = null
    private var calendarJob: Job? = null
    private var bookingsJob: Job? = null
    private var carsGeneration = 0L
    private var bookingsGeneration = 0L
    val state = _state.asStateFlow()

    fun resetSession() {
        carsJob?.cancel()
        calendarJob?.cancel()
        bookingsJob?.cancel()
        carsGeneration++
        bookingsGeneration++
        carsJob = null
        calendarJob = null
        bookingsJob = null
        viewModelScope.coroutineContext.cancelChildren()
        _state.value = RentalUiState()
    }

    fun loadVendor() {
        viewModelScope.launch {
            _state.value = _state.value.copy(vendorLoading = true, vendorError = null)
            repository.rentalVendor()
                .onSuccess { _state.value = _state.value.copy(vendor = it, vendorLoading = false) }
                .onFailure { _state.value = _state.value.copy(vendorLoading = false, vendorError = it.message ?: "Unable to load rental vendor profile") }
        }
    }

    fun clearCarSearch() {
        carsJob?.cancel()
        carsGeneration++
        _state.value = _state.value.copy(cars = emptyList(), marketplaceLoading = false, marketplaceError = null)
    }

    fun loadCars(startDate: String? = null, endDate: String? = null, location: String? = null) {
        carsJob?.cancel()
        val generation = ++carsGeneration
        carsJob = viewModelScope.launch {
            _state.value = _state.value.copy(marketplaceLoading = true, marketplaceError = null)
            repository.rentalCars(
                startDate?.takeIf { it.isNotBlank() },
                endDate?.takeIf { it.isNotBlank() },
                location?.trim()?.takeIf { !it.isNullOrBlank() }
            )
                .onSuccess {
                    if (generation != carsGeneration) return@onSuccess
                    _state.value = _state.value.copy(cars = it, marketplaceLoading = false, marketplaceError = null)
                }
                .onFailure { failure ->
                    if (generation != carsGeneration || !kotlinx.coroutines.currentCoroutineContext().isActive) return@onFailure
                    _state.value = _state.value.copy(marketplaceLoading = false, marketplaceError = failure.message ?: "Unable to load rental cars")
                }
        }
    }


    fun loadBookings() {
        bookingsJob?.cancel()
        val generation = ++bookingsGeneration
        bookingsJob = viewModelScope.launch {
            _state.value = _state.value.copy(bookingsLoading = true, bookingsError = null)
            repository.rentalBookings()
                .onSuccess { response ->
                    if (generation != bookingsGeneration) return@onSuccess
                    _state.value = _state.value.copy(bookings = response.items, bookingsLoading = false, bookingsError = null)
                }
                .onFailure { e ->
                    if (generation != bookingsGeneration || !kotlinx.coroutines.currentCoroutineContext().isActive) return@onFailure
                    _state.value = _state.value.copy(bookingsLoading = false, bookingsError = e.message ?: "Unable to load rental bookings")
                }
        }
    }

    fun cancelBooking(bookingId: String, onDone: () -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, mutationError = null)
            repository.cancelRentalBooking(bookingId)
                .onSuccess { cancelled ->
                    _state.value = _state.value.copy(
                        bookings = _state.value.bookings.map { if (it.bookingId == cancelled.bookingId) cancelled else it },
                        saving = false
                    )
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(saving = false, mutationError = it.message ?: "Unable to cancel booking") }
        }
    }

    fun createBooking(request: RentalBookingRequest, onDone: () -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, mutationError = null)
            repository.createRentalBooking(request)
                .onSuccess { booking -> _state.value = _state.value.copy(bookings = listOf(booking) + _state.value.bookings.filterNot { it.bookingId == booking.bookingId }, saving = false); onDone() }
                .onFailure { _state.value = _state.value.copy(saving = false, mutationError = it.message ?: "Unable to create booking") }
        }
    }

    fun quoteBooking(request: RentalBookingQuoteRequest, onDone: (RentalBookingQuoteResponse) -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, mutationError = null)
            repository.rentalBookingQuote(request)
                .onSuccess { _state.value = _state.value.copy(saving = false); onDone(it) }
                .onFailure { _state.value = _state.value.copy(saving = false, mutationError = it.message ?: "Unable to calculate rental quote") }
        }
    }


    fun updateVendor(request: RentalVendorUpdateRequest, onDone: () -> Unit = {}) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, mutationError = null)
            repository.updateRentalVendor(request)
                .onSuccess {
                    _state.value = _state.value.copy(vendor = it, saving = false)
                    onDone()
                }
                .onFailure {
                    _state.value = _state.value.copy(saving = false, mutationError = it.message ?: "Unable to update vendor profile")
                }
        }
    }

    fun loadVendorPayouts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(payoutsLoading = true, payoutsError = null)
            val payoutsRequest = async { repository.rentalVendorPayouts() }
            val earningsRequest = async { repository.rentalVendorEarnings() }

            payoutsRequest.await()
                .onSuccess { _state.value = _state.value.copy(payouts = it) }
                .onFailure { _state.value = _state.value.copy(payoutsError = it.message ?: "Unable to load vendor payouts") }

            earningsRequest.await()
                .onSuccess { _state.value = _state.value.copy(earnings = it) }
                .onFailure {
                    if (_state.value.payoutsError == null) {
                        _state.value = _state.value.copy(payoutsError = it.message ?: "Unable to load rental earnings")
                    }
                }

            _state.value = _state.value.copy(payoutsLoading = false)
        }
    }
    fun loadVendorVehicles() {
        viewModelScope.launch {
            _state.value = _state.value.copy(vendorVehiclesLoading = true, vendorVehiclesError = null)
            repository.rentalVendorVehicles()
                .onSuccess { _state.value = _state.value.copy(vendorCars = it, vendorVehiclesLoading = false) }
                .onFailure { _state.value = _state.value.copy(vendorVehiclesLoading = false, vendorVehiclesError = it.message ?: "Unable to load vendor vehicles") }
        }
    }

    fun takeVehicleOffMarket(
        carId: String,
        request: RentalVehicleUnavailabilityRequest,
        onDone: () -> Unit
    ) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, mutationError = null)
            repository.takeRentalVehicleOffMarket(carId, request)
                .onSuccess { created ->
                    val current = _state.value.vehicleUnavailabilityByCar[carId].orEmpty()
                    _state.value = _state.value.copy(
                        vehicleUnavailabilityByCar = _state.value.vehicleUnavailabilityByCar + (carId to (current + created).sortedBy { it.startDate }),
                        saving = false
                    )
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(saving = false, mutationError = it.message ?: "Unable to take vehicle off market") }
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
                .onFailure { _state.value = _state.value.copy(availabilityError = it.message ?: "Unable to load vehicle availability") }
        }
    }

    fun restoreVehicleToMarket(carId: String, unavailableId: String, onDone: () -> Unit = {}) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, mutationError = null)
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
                .onFailure { _state.value = _state.value.copy(saving = false, mutationError = it.message ?: "Unable to restore vehicle to market") }
        }
    }

    fun loadVehicleCalendar(carId: String, year: Int, month: Int) {
        calendarJob?.cancel()
        calendarJob = viewModelScope.launch {
            _state.value = _state.value.copy(calendarLoading = true, calendarError = null)
            repository.rentalVehicleCalendar(carId, year, month)
                .onSuccess { response ->
                    if (kotlinx.coroutines.currentCoroutineContext().isActive) {
                        _state.value = _state.value.copy(vehicleCalendar = response, calendarLoading = false)
                    }
                }
                .onFailure { failure ->
                    if (kotlinx.coroutines.currentCoroutineContext().isActive) {
                        _state.value = _state.value.copy(
                            calendarLoading = false,
                            calendarError = failure.message ?: "Unable to load vehicle calendar"
                        )
                    }
                }
        }
    }

    fun resubmitVehicle(
        carId: String,
        request: RentalVehicleUpdateRequest,
        photoChanges: RentalVehiclePhotoChanges,
        onDone: () -> Unit
    ) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, mutationError = null)
            repository.resubmitRentalVehicle(carId, request)
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        vendorCars = _state.value.vendorCars.map { if (it.id == updated.id) updated else it }
                    )
                    uploadRentalVehiclePhotos(carId, photoChanges.vehiclePhotos)
                        .onSuccess { photoUpdated ->
                            uploadDriverPhotoIfNeeded(photoUpdated, photoChanges.driverPhoto)
                                .onSuccess { driverUpdated ->
                                    _state.value = _state.value.copy(
                                        vendorCars = _state.value.vendorCars.map { if (it.id == driverUpdated.id) driverUpdated else it },
                                        saving = false
                                    )
                                    onDone()
                                }
                                .onFailure {
                                    _state.value = _state.value.copy(
                                        saving = false,
                                        mutationError = it.message ?: "Vehicle submitted, but the driver photo could not be saved"
                                    )
                                }
                        }
                        .onFailure {
                            _state.value = _state.value.copy(
                                saving = false,
                                mutationError = it.message ?: "Vehicle submitted, but one or more photos could not be saved"
                            )
                        }
                }
                .onFailure { _state.value = _state.value.copy(saving = false, mutationError = it.message ?: "Unable to resubmit vehicle") }
        }
    }

    fun onboardVehicle(
        request: RentalVehicleOnboardingRequest,
        photoChanges: RentalVehiclePhotoChanges,
        onDone: () -> Unit
    ) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, mutationError = null)
            repository.onboardRentalVehicle(request)
                .onSuccess { created ->
                    _state.value = _state.value.copy(vendorCars = _state.value.vendorCars + created)
                    uploadRentalVehiclePhotos(created.id, photoChanges.vehiclePhotos)
                        .onSuccess { photoUpdated ->
                            uploadDriverPhotoIfNeeded(photoUpdated, photoChanges.driverPhoto)
                                .onSuccess { driverUpdated ->
                                    _state.value = _state.value.copy(
                                        vendorCars = _state.value.vendorCars.map { if (it.id == driverUpdated.id) driverUpdated else it },
                                        saving = false
                                    )
                                    onDone()
                                }
                                .onFailure {
                                    _state.value = _state.value.copy(
                                        saving = false,
                                        mutationError = it.message ?: "Vehicle created, but the driver photo could not be saved"
                                    )
                                }
                        }
                        .onFailure {
                            _state.value = _state.value.copy(
                                saving = false,
                                mutationError = it.message ?: "Vehicle created, but one or more photos could not be saved"
                            )
                        }
                }
                .onFailure { _state.value = _state.value.copy(saving = false, mutationError = it.message ?: "Unable to submit vehicle") }
        }
    }

    private suspend fun uploadDriverPhotoIfNeeded(
        current: RentalCarResponse,
        candidate: RentalPhotoCandidate?
    ): Result<RentalCarResponse> = try {
        if (candidate == null) {
            Result.success(
                _state.value.vendorCars.firstOrNull { it.id == current.id } ?: current
            )
        } else {
            require(!current.driverId.isNullOrBlank()) { "Vehicle driver id is missing" }
            when (candidate.source) {
                RentalPhotoCandidateSource.DEVICE ->
                    repository.uploadRentalDriverPhoto(
                        current.driverId,
                        android.net.Uri.parse(candidate.value)
                    )
                RentalPhotoCandidateSource.URL ->
                    repository.importRentalDriverPhotoFromUrl(
                        current.driverId,
                        candidate.value
                    )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun uploadRentalVehiclePhotos(
        carId: String,
        candidates: Map<Int, RentalPhotoCandidate>
    ): Result<RentalCarResponse> = try {
        var current: RentalCarResponse? = _state.value.vendorCars.firstOrNull { it.id == carId }
        candidates.toSortedMap().forEach { (slot, candidate) ->
            current = when (candidate.source) {
                RentalPhotoCandidateSource.DEVICE ->
                    repository.uploadRentalVehiclePhoto(
                        carId, slot, android.net.Uri.parse(candidate.value)
                    ).getOrThrow()
                RentalPhotoCandidateSource.URL ->
                    repository.importRentalVehiclePhotoFromUrl(
                        carId, slot, candidate.value
                    ).getOrThrow()
            }
            _state.value = _state.value.copy(
                vendorCars = _state.value.vendorCars.map { if (it.id == current!!.id) current!! else it }
            )
        }
        Result.success(current ?: _state.value.vendorCars.firstOrNull { it.id == carId }
            ?: error("Vehicle not found after photo update"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun onboardVendor(request: RentalVendorOnboardingRequest, onDone: () -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, mutationError = null)
            repository.onboardRentalVendor(request)
                .onSuccess { _state.value = _state.value.copy(vendor = it, saving = false); onDone() }
                .onFailure { _state.value = _state.value.copy(saving = false, mutationError = it.message ?: "Unable to submit vendor onboarding") }
        }
    }
}
