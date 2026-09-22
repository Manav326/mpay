package com.recharge.backend.api

import com.recharge.backend.service.RentalService
import com.recharge.backend.service.RoleAccessService
import com.recharge.backend.repository.UserRepository
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

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
        return rentalService.approveVendor(vendorId)
    }

    @PostMapping("/admin/vendors/{vendorId}/reject")
    fun rejectVendor(
        authentication: Authentication,
        @PathVariable vendorId: Long,
        @RequestBody(required = false) request: RentalAdminDecisionRequest?
    ): RentalVendorResponse {
        requireAdmin(authentication)
        return rentalService.rejectVendor(vendorId, request?.reason)
    }

    @PostMapping("/admin/vehicles/{carId}/approve")
    fun approveVehicle(authentication: Authentication, @PathVariable carId: Long): RentalCarResponse {
        requireAdmin(authentication)
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
