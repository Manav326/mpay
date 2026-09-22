package com.recharge.backend.api

import com.recharge.backend.service.RentalService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/car-rental")
class RentalController(private val rentalService: RentalService) {
    private fun userId(authentication: Authentication): Long =
        authentication.name.toLongOrNull() ?: throw IllegalStateException("Invalid authenticated user")

    @GetMapping("/cars")
    fun cars(): List<RentalCarResponse> = rentalService.availableCars()

    @GetMapping("/vendor")
    fun vendor(authentication: Authentication): RentalVendorResponse =
        rentalService.vendor(userId(authentication))

    @PostMapping("/vendor")
    fun onboardVendor(
        authentication: Authentication,
        @Valid @RequestBody request: RentalVendorOnboardingRequest
    ): RentalVendorResponse = rentalService.onboardVendor(userId(authentication), request)

    @GetMapping("/vendor/vehicles")
    fun vendorVehicles(authentication: Authentication): List<RentalCarResponse> =
        rentalService.vendorCars(userId(authentication))

    @PostMapping("/vendor/vehicles")
    fun onboardVehicle(
        authentication: Authentication,
        @Valid @RequestBody request: RentalVehicleOnboardingRequest
    ): RentalCarResponse = rentalService.onboardVehicle(userId(authentication), request)

    @PostMapping("/admin/vendors/{vendorId}/approve")
    fun approveVendor(authentication: Authentication, @PathVariable vendorId: Long): RentalVendorResponse {
        authentication.name.toLongOrNull() ?: throw IllegalStateException("Invalid authenticated user")
        return rentalService.approveVendor(vendorId)
    }

    @PostMapping("/admin/vehicles/{carId}/approve")
    fun approveVehicle(authentication: Authentication, @PathVariable carId: Long): RentalCarResponse {
        authentication.name.toLongOrNull() ?: throw IllegalStateException("Invalid authenticated user")
        return rentalService.approveVehicle(carId)
    }

    @GetMapping("/bookings")
    fun bookings(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): RentalBookingPageResponse = rentalService.bookings(userId(authentication), page, size)

    @PostMapping("/bookings/{bookingId}/cancel")
    fun cancelBooking(
        authentication: Authentication,
        @PathVariable bookingId: String
    ): RentalBookingResponse = rentalService.cancelBooking(userId(authentication), bookingId)

    @PostMapping("/bookings")
    fun createBooking(
        authentication: Authentication,
        @Valid @RequestBody request: RentalBookingRequest
    ): RentalBookingResponse = rentalService.createBooking(userId(authentication), request)
}
