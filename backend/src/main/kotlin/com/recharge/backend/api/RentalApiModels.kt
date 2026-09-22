package com.recharge.backend.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class RentalCarResponse(
    val id: String,
    val name: String,
    val category: String,
    val seats: Int,
    val transmission: String,
    val fuelType: String?,
    val registrationYear: Int?,
    val city: String?,
    val pickupAddress: String?,
    val imageUrl: String?,
    val pricePerDay: BigDecimal,
    val driverName: String,
    val driverMobile: String? = null,
    val driverRating: BigDecimal? = null,
    val approvalStatus: String? = null,
    val rejectionReason: String? = null
)

data class RentalVendorResponse(
    val vendorId: String?,
    val status: String,
    val vendorType: String?,
    val fullName: String?,
    val businessName: String?,
    val city: String?,
    val state: String?,
    val vehicleCount: Int,
    val address: String? = null,
    val pinCode: String? = null,
    val panNumber: String? = null,
    val payoutUpiId: String? = null,
    val bankAccountNumber: String? = null,
    val bankIfsc: String? = null,
    val rejectionReason: String? = null,
    val submittedAt: Instant? = null
)

data class RentalAdminVendorResponse(
    val vendorId: String,
    val userId: String,
    val fullName: String,
    val businessName: String?,
    val mobile: String?,
    val email: String?,
    val vendorType: String,
    val status: String,
    val address: String,
    val city: String,
    val state: String,
    val pinCode: String,
    val panNumber: String?,
    val payoutUpiId: String?,
    val bankAccountNumber: String?,
    val bankIfsc: String?,
    val vehicleCount: Int,
    val rejectionReason: String?,
    val submittedAt: Instant,
    val updatedAt: Instant
)

data class RentalAdminDecisionRequest(
    @field:Size(max = 500) val reason: String? = null
)

data class RentalVendorOnboardingRequest(
    @field:NotBlank val vendorType: String,
    @field:NotBlank @field:Size(max = 120) val fullName: String,
    @field:Size(max = 160) val businessName: String? = null,
    @field:NotBlank @field:Size(max = 300) val address: String,
    @field:NotBlank @field:Size(max = 100) val city: String,
    @field:NotBlank @field:Size(max = 100) val state: String,
    @field:NotBlank @field:Size(max = 10) val pinCode: String,
    @field:Size(max = 20) val panNumber: String? = null,
    @field:Size(max = 254) val payoutUpiId: String? = null,
    @field:Size(max = 64) val bankAccountNumber: String? = null,
    @field:Size(max = 20) val bankIfsc: String? = null
)

data class RentalDriverRequest(
    @field:NotBlank @field:Size(max = 120) val fullName: String,
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Driver mobile must be a valid 10 digit Indian mobile number")
    val mobile: String,
    @field:NotBlank @field:Size(max = 64) val licenseNumber: String,
    val licenseExpiry: LocalDate,
    @field:Size(max = 300) val address: String? = null
)

data class RentalVehicleOnboardingRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:NotBlank @field:Size(max = 50) val category: String,
    val seats: Int,
    @field:NotBlank @field:Size(max = 30) val transmission: String,
    @field:NotBlank @field:Size(max = 30) val fuelType: String,
    val manufacturingYear: Int,
    val registrationYear: Int,
    @field:NotBlank @field:Size(max = 32) val registrationNumber: String,
    @field:NotBlank @field:Size(max = 80) val make: String,
    @field:NotBlank @field:Size(max = 80) val model: String,
    @field:Size(max = 80) val variant: String? = null,
    @field:NotBlank @field:Size(max = 300) val pickupAddress: String,
    @field:NotBlank @field:Size(max = 100) val city: String,
    @field:NotBlank @field:Size(max = 100) val state: String,
    val pricePerDay: BigDecimal,
    @field:Size(max = 500) val imageUrl: String? = null,
    val driver: RentalDriverRequest
)

data class RentalBookingQuoteRequest(
    @field:NotBlank val carId: String,
    @field:NotBlank val pickupLocation: String,
    @field:NotBlank val dropLocation: String,
    val startDate: LocalDate,
    val endDate: LocalDate
)

data class RentalBookingQuoteResponse(
    val carId: String,
    val carName: String,
    val driverName: String,
    val pickup: String,
    val drop: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: Long,
    val pricePerDay: BigDecimal,
    val total: BigDecimal
)

data class RentalBookingRequest(
    @field:NotBlank val carId: String,
    @field:NotBlank val pickupLocation: String,
    @field:NotBlank val dropLocation: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val paymentMethod: String = "WALLET"
)

data class RentalBookingResponse(
    val bookingId: String,
    val carName: String,
    val driverName: String,
    val driverMobile: String? = null,
    val pickup: String,
    val drop: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val total: BigDecimal,
    val paymentMethod: String,
    val status: String,
    val createdAt: Instant
)

data class RentalBookingPageResponse(
    val items: List<RentalBookingResponse>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)
