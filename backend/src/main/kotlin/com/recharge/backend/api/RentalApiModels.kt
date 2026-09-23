package com.recharge.backend.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

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
    val driverId: String? = null,
    val driverName: String,
    val driverMobile: String? = null,
    val driverPhotoUrl: String? = null,
    val driverRating: BigDecimal? = null,
    val approvalStatus: String? = null,
    val rejectionReason: String? = null,
    val make: String? = null,
    val model: String? = null,
    val variant: String? = null,
    val manufacturingYear: Int? = null,
    val registrationNumber: String? = null,
    val state: String? = null,
    val driverLicenseNumber: String? = null,
    val driverLicenseExpiry: LocalDateTime? = null,
    val driverAddress: String? = null
)

data class RentalPublicCarResponse(
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
    val driverPhotoUrl: String? = null,
    val driverRating: BigDecimal? = null,
    val make: String? = null,
    val model: String? = null,
    val variant: String? = null,
    val manufacturingYear: Int? = null,
    val state: String? = null
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
    val bankName: String? = null,
    val payoutPrimaryMethod: String? = null,
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
    val bankName: String? = null,
    val payoutPrimaryMethod: String? = null,
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
    @field:Size(max = 20) val bankIfsc: String? = null,
    @field:Size(max = 120) val bankName: String? = null,
    @field:Pattern(regexp = "^(BANK|UPI)$", message = "Primary payout method must be BANK or UPI") val payoutPrimaryMethod: String? = null
)

data class RentalVendorUpdateRequest(
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
    @field:Size(max = 20) val bankIfsc: String? = null,
    @field:Size(max = 120) val bankName: String? = null,
    @field:Pattern(regexp = "^(BANK|UPI)$", message = "Primary payout method must be BANK or UPI") val payoutPrimaryMethod: String? = null
)

data class RentalDriverRequest(
    @field:NotBlank @field:Size(max = 120) val fullName: String,
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Driver mobile must be a valid 10 digit Indian mobile number")
    val mobile: String,
    @field:NotBlank @field:Size(max = 64) val licenseNumber: String,
    val licenseExpiry: LocalDateTime,
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

data class RentalVehicleUpdateRequest(
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
    val startDate: LocalDateTime,
    val endDate: LocalDateTime
)

data class RentalBookingQuoteResponse(
    val carId: String,
    val carName: String,
    val driverName: String,
    val pickup: String,
    val drop: String,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val days: Long,
    val pricePerDay: BigDecimal,
    val total: BigDecimal
)

data class RentalBookingRequest(
    @field:NotBlank @field:Size(max = 100) val clientRequestId: String,
    @field:NotBlank val carId: String,
    @field:NotBlank val pickupLocation: String,
    @field:NotBlank val dropLocation: String,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val paymentMethod: String = "WALLET"
)

data class RentalBookingResponse(
    val bookingId: String,
    val carName: String,
    val driverName: String,
    val driverMobile: String? = null,
    val carImageUrl: String? = null,
    val pickup: String,
    val drop: String,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
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


data class RentalVendorPayoutResponse(
    val payoutId: String,
    val bookingId: String,
    val carId: String,
    val carName: String,
    val grossAmount: BigDecimal,
    val platformFeePercent: BigDecimal,
    val platformFeeAmount: BigDecimal,
    val vendorNetAmount: BigDecimal,
    val status: String,
    val createdAt: Instant,
    val paidAt: Instant?
)


data class RentalVendorEarningsPeriodResponse(
    val grossAmount: BigDecimal,
    val platformFeeAmount: BigDecimal,
    val vendorNetAmount: BigDecimal,
    val bookingCount: Long,
    val completedBookingCount: Long
)

data class RentalVendorEarningsResponse(
    val today: RentalVendorEarningsPeriodResponse,
    val monthly: RentalVendorEarningsPeriodResponse,
    val upcomingBookingCount: Long
)

data class RentalVehicleUnavailabilityRequest(
    @field:NotBlank val reasonCode: String,
    @field:Size(max = 300) val reasonNote: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate
)

data class RentalVehicleUnavailabilityResponse(
    val id: String,
    val carId: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reasonCode: String,
    val reasonLabel: String,
    val reasonNote: String?,
    val status: String,
    val createdAt: Instant
)

data class RentalVehicleCalendarDayResponse(
    val date: LocalDate,
    val status: String,
    val bookingId: String? = null,
    val reasonCode: String? = null,
    val reasonLabel: String? = null
)

data class RentalVehicleCalendarResponse(
    val carId: String,
    val carName: String,
    val year: Int,
    val month: Int,
    val days: List<RentalVehicleCalendarDayResponse>
)

data class RentalAdminVehicleUnavailabilityResponse(
    val id: String,
    val carId: String,
    val carName: String,
    val vendorId: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reasonCode: String,
    val reasonLabel: String,
    val reasonNote: String?,
    val status: String,
    val createdAt: Instant
)


data class RentalAdminBookingResponse(
    val bookingId: String,
    val userId: String,
    val userName: String?,
    val userMobile: String?,
    val carId: String,
    val carName: String,
    val vendorName: String?,
    val pickup: String,
    val drop: String,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val total: BigDecimal,
    val paymentMethod: String,
    val paymentStatus: String,
    val walletLedgerRef: String?,
    val status: String,
    val createdAt: Instant
)

data class RentalAdminBookingPageResponse(
    val items: List<RentalAdminBookingResponse>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class RentalAdminDashboardResponse(
    val totalBookings: Long,
    val confirmedBookings: Long,
    val activeBookings: Long,
    val completedBookings: Long,
    val cancelledBookings: Long,
    val totalBookingValue: BigDecimal,
    val totalRefunded: BigDecimal,
    val totalVendorPayouts: BigDecimal,
    val totalPlatformFees: BigDecimal
)


data class RentalAdminPayoutResponse(
    val payoutId: String,
    val bookingId: String,
    val vendorId: String,
    val vendorName: String,
    val grossAmount: BigDecimal,
    val platformFeePercent: BigDecimal,
    val platformFeeAmount: BigDecimal,
    val vendorNetAmount: BigDecimal,
    val status: String,
    val walletLedgerRef: String?,
    val failureReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val paidAt: Instant?
)

data class RentalAdminPayoutPageResponse(
    val items: List<RentalAdminPayoutResponse>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)
