package com.recharge.backend.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class RentalCarResponse(
    val id: String,
    val name: String,
    val category: String,
    val seats: Int,
    val transmission: String,
    val pricePerDay: BigDecimal
)

data class RentalBookingRequest(
    @field:NotBlank val carId: String,
    @field:NotBlank val pickupLocation: String,
    @field:NotBlank val dropLocation: String,
    val startDate: LocalDate,
    val endDate: LocalDate
)

data class RentalBookingResponse(
    val bookingId: String,
    val carName: String,
    val pickup: String,
    val drop: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val total: BigDecimal,
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
