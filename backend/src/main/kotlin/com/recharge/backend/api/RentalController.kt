package com.recharge.backend.api

import com.recharge.backend.service.RentalService
import com.recharge.backend.service.RoleAccessService
import com.recharge.backend.repository.UserRepository
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/car-rental")
class RentalController(
    private val rentalService: RentalService,
    private val users: UserRepository,
    private val roleAccessService: RoleAccessService
) {
    private fun userId(authentication: Authentication): Long =
        authentication.name.toLongOrNull() ?: throw IllegalStateException("Invalid authenticated user")

    @GetMapping("/cars")
    fun cars(
        authentication: Authentication,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): List<RentalCarResponse> {
        val parsedStart = startDate?.takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) }
        val parsedEnd = endDate?.takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) }
        return rentalService.availableCars(userId(authentication), parsedStart, parsedEnd)
    }

    @GetMapping("/vendor")
    fun vendor(authentication: Authentication): RentalVendorResponse =
        rentalService.vendor(userId(authentication))

    @PostMapping("/vendor")
    fun onboardVendor(
        authentication: Authentication,
        @Valid @RequestBody request: RentalVendorOnboardingRequest
    ): RentalVendorResponse = rentalService.onboardVendor(userId(authentication), request)

    @GetMapping("/vendor/payouts")
    fun vendorPayouts(authentication: Authentication): List<RentalVendorPayoutResponse> =
        rentalService.vendorPayouts(userId(authentication))

    @GetMapping("/vendor/vehicles")
    fun vendorVehicles(authentication: Authentication): List<RentalCarResponse> =
        rentalService.vendorCars(userId(authentication))

    @PostMapping("/vendor/vehicles")
    fun onboardVehicle(
        authentication: Authentication,
        @Valid @RequestBody request: RentalVehicleOnboardingRequest
    ): RentalCarResponse = rentalService.onboardVehicle(userId(authentication), request)

    @PutMapping("/vendor/vehicles/{carId}")
    fun resubmitVehicle(
        authentication: Authentication,
        @PathVariable carId: Long,
        @Valid @RequestBody request: RentalVehicleUpdateRequest
    ): RentalCarResponse =
        rentalService.resubmitVehicle(userId(authentication), carId, request)

    private fun requireAdmin(authentication: Authentication) {
        val id = authentication.name.toLongOrNull() ?: throw IllegalStateException("Invalid authenticated user")
        val user = users.findById(id).orElseThrow { IllegalArgumentException("User not found") }
        roleAccessService.requirePermission(user, "MANAGE_VENDORS")
    }

    @GetMapping("/admin/vendors")
    fun adminVendors(authentication: Authentication): List<RentalAdminVendorResponse> {
        requireAdmin(authentication)
        return rentalService.adminVendors()
    }

    @GetMapping("/admin/vendors/{vendorId}/vehicles")
    fun adminVendorVehicles(authentication: Authentication, @PathVariable vendorId: Long): List<RentalCarResponse> {
        requireAdmin(authentication)
        return rentalService.adminVendorCars(vendorId)
    }

    @PostMapping("/admin/vendors/{vendorId}/approve")
    fun approveVendor(authentication: Authentication, @PathVariable vendorId: Long): RentalVendorResponse {
        requireAdmin(authentication)
        return rentalService.approveVendor(vendorId, userId(authentication))
    }

    @PostMapping("/admin/vendors/{vendorId}/reject")
    fun rejectVendor(
        authentication: Authentication,
        @PathVariable vendorId: Long,
        @RequestBody(required = false) request: RentalAdminDecisionRequest?
    ): RentalVendorResponse {
        requireAdmin(authentication)
        return rentalService.rejectVendor(vendorId, request?.reason, userId(authentication))
    }

    @PostMapping("/admin/bookings/{bookingId}/complete")
    fun completeBooking(authentication: Authentication, @PathVariable bookingId: String): RentalBookingResponse {
        requireAdmin(authentication)
        return rentalService.completeBooking(bookingId, userId(authentication))
    }

    @PostMapping("/admin/vehicles/{carId}/approve")
    fun approveVehicle(authentication: Authentication, @PathVariable carId: Long): RentalCarResponse {
        requireAdmin(authentication)
        return rentalService.approveVehicle(carId, userId(authentication))
    }

    @PostMapping("/admin/vehicles/{carId}/reject")
    fun rejectVehicle(
        authentication: Authentication,
        @PathVariable carId: Long,
        @RequestBody(required = false) request: RentalAdminDecisionRequest?
    ): RentalCarResponse {
        requireAdmin(authentication)
        return rentalService.rejectVehicle(carId, request?.reason, userId(authentication))
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

    @PostMapping("/bookings/quote")
    fun quoteBooking(authentication: Authentication, @Valid @RequestBody request: RentalBookingQuoteRequest): RentalBookingQuoteResponse = rentalService.quoteBooking(userId(authentication), request)

    @PostMapping("/bookings")
    fun createBooking(
        authentication: Authentication,
        @Valid @RequestBody request: RentalBookingRequest
    ): RentalBookingResponse = rentalService.createBooking(userId(authentication), request)
}
