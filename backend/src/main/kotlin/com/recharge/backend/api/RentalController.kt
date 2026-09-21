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

    @GetMapping("/bookings")
    fun bookings(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): RentalBookingPageResponse = rentalService.bookings(userId(authentication), page, size)

    @PostMapping("/bookings")
    fun createBooking(
        authentication: Authentication,
        @Valid @RequestBody request: RentalBookingRequest
    ): RentalBookingResponse = rentalService.createBooking(userId(authentication), request)
}
