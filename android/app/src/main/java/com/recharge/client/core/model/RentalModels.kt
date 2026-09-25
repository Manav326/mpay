package com.recharge.client.core.model

import java.math.BigDecimal

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
    val submittedAt: String? = null
)

data class RentalVendorOnboardingRequest(
    val vendorType: String,
    val fullName: String,
    val businessName: String? = null,
    val address: String,
    val city: String,
    val state: String,
    val pinCode: String,
    val panNumber: String? = null,
    val payoutUpiId: String? = null,
    val bankAccountNumber: String? = null,
    val bankIfsc: String? = null,
    val bankName: String? = null,
    val payoutPrimaryMethod: String? = null
)

data class RentalVendorUpdateRequest(
    val vendorType: String,
    val fullName: String,
    val businessName: String? = null,
    val address: String,
    val city: String,
    val state: String,
    val pinCode: String,
    val panNumber: String? = null,
    val payoutUpiId: String? = null,
    val bankAccountNumber: String? = null,
    val bankIfsc: String? = null,
    val bankName: String? = null,
    val payoutPrimaryMethod: String? = null
)

data class RentalLocationInput(
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val placeId: String? = null
)

data class RentalDriverRequest(
    val fullName: String,
    val mobile: String,
    val licenseNumber: String,
    val licenseExpiry: String,
    val address: String? = null
)

data class RentalVehicleOnboardingRequest(
    val name: String,
    val category: String,
    val seats: Int,
    val transmission: String,
    val fuelType: String,
    val manufacturingYear: Int,
    val registrationYear: Int,
    val registrationNumber: String,
    val make: String,
    val model: String,
    val variant: String? = null,
    val pickupAddress: String,
    val city: String,
    val state: String,
    val pricePerDay: BigDecimal,
    val pickupLocation: RentalLocationInput? = null,
    val imageUrl: String? = null,
    val driver: RentalDriverRequest
)

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
    val pickupLatitude: Double? = null,
    val pickupLongitude: Double? = null,
    val pickupPlaceId: String? = null,
    val approvalStatus: String? = null,
    val rejectionReason: String? = null,
    val make: String? = null,
    val model: String? = null,
    val variant: String? = null,
    val manufacturingYear: Int? = null,
    val registrationNumber: String? = null,
    val state: String? = null,
    val driverLicenseNumber: String? = null,
    val driverLicenseExpiry: String? = null,
    val driverAddress: String? = null
)

data class RentalVehicleUpdateRequest(
    val name: String,
    val category: String,
    val seats: Int,
    val transmission: String,
    val fuelType: String,
    val manufacturingYear: Int,
    val registrationYear: Int,
    val registrationNumber: String,
    val make: String,
    val model: String,
    val variant: String? = null,
    val pickupAddress: String,
    val city: String,
    val state: String,
    val pricePerDay: BigDecimal,
    val pickupLocation: RentalLocationInput? = null,
    val imageUrl: String? = null,
    val driver: RentalDriverRequest
)

data class RentalBookingQuoteRequest(
    val carId: String,
    val pickupLocation: String,
    val dropLocation: String,
    val pickupCoordinates: RentalLocationInput? = null,
    val dropCoordinates: RentalLocationInput? = null,
    val startDate: String,
    val endDate: String
)

data class RentalBookingQuoteResponse(
    val carId: String,
    val carName: String,
    val driverName: String,
    val pickup: String,
    val drop: String,
    val startDate: String,
    val endDate: String,
    val days: Long,
    val pricePerDay: BigDecimal,
    val total: BigDecimal
)

data class RentalBookingRequest(
    val clientRequestId: String,
    val carId: String,
    val pickupLocation: String,
    val dropLocation: String,
    val pickupCoordinates: RentalLocationInput? = null,
    val dropCoordinates: RentalLocationInput? = null,
    val startDate: String,
    val endDate: String,
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
    val pickupLatitude: Double? = null,
    val pickupLongitude: Double? = null,
    val pickupPlaceId: String? = null,
    val dropLatitude: Double? = null,
    val dropLongitude: Double? = null,
    val dropPlaceId: String? = null,
    val startDate: String,
    val endDate: String,
    val total: BigDecimal,
    val paymentMethod: String,
    val status: String,
    val createdAt: String
)

data class RentalBookingPageResponse(val items: List<RentalBookingResponse>, val page: Int, val size: Int, val totalItems: Long, val totalPages: Int, val hasNext: Boolean)


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
    val createdAt: String,
    val paidAt: String? = null
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
    val reasonCode: String,
    val reasonNote: String? = null,
    val startDate: String,
    val endDate: String
)

data class RentalVehicleUnavailabilityResponse(
    val id: String,
    val carId: String,
    val startDate: String,
    val endDate: String,
    val reasonCode: String,
    val reasonLabel: String,
    val reasonNote: String? = null,
    val status: String,
    val createdAt: String
)

data class RentalVehicleCalendarDayResponse(
    val date: String,
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
