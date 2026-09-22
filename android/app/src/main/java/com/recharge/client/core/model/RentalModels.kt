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
    val bankIfsc: String? = null
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
    val driverName: String,
    val driverMobile: String? = null,
    val driverRating: BigDecimal? = null
)

data class RentalBookingRequest(
    val carId: String,
    val pickupLocation: String,
    val dropLocation: String,
    val startDate: String,
    val endDate: String,
    val paymentMethod: String = "WALLET"
)

data class RentalBookingResponse(
    val bookingId: String,
    val carName: String,
    val driverName: String,
    val driverMobile: String? = null,
    val pickup: String,
    val drop: String,
    val startDate: String,
    val endDate: String,
    val total: BigDecimal,
    val paymentMethod: String,
    val status: String,
    val createdAt: String
)

data class RentalBookingPageResponse(val items: List<RentalBookingResponse>, val page: Int, val size: Int, val totalItems: Long, val totalPages: Int, val hasNext: Boolean)
