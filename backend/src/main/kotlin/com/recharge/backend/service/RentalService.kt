package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.domain.RentalBookingEntity
import com.recharge.backend.domain.RentalCarEntity
import com.recharge.backend.repository.RentalBookingRepository
import com.recharge.backend.repository.RentalCarRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class RentalService(
    private val cars: RentalCarRepository,
    private val bookings: RentalBookingRepository,
    private val wallet: WalletService
) {
    fun availableCars(): List<RentalCarResponse> =
        cars.findAllByActiveTrueOrderByPricePerDayAsc().map(::toCarResponse)

    fun bookings(userId: Long, page: Int, size: Int): RentalBookingPageResponse {
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
        val result = bookings.findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
        val carMap = cars.findAllById(result.content.map { it.carId }).associateBy { it.id }
        return RentalBookingPageResponse(
            items = result.content.map { toBookingResponse(it, carMap[it.carId]?.name ?: "Car") },
            page = result.number,
            size = result.size,
            totalItems = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext()
        )
    }

    @Transactional
    fun createBooking(userId: Long, request: RentalBookingRequest): RentalBookingResponse {
        val carId = request.carId.toLongOrNull() ?: throw IllegalArgumentException("Invalid car id")
        val car = cars.findByIdForUpdate(carId).orElseThrow { IllegalArgumentException("Rental car not found") }
        check(car.active) { "Rental car is not available" }
        val pickup = request.pickupLocation.trim()
        val drop = request.dropLocation.trim()
        require(pickup.isNotBlank()) { "Pickup location is required" }
        require(drop.isNotBlank()) { "Drop location is required" }
        require(request.endDate.isAfter(request.startDate)) { "End date must be after start date" }
        require(!request.startDate.isBefore(LocalDate.now())) { "Start date cannot be in the past" }

        val overlappingStatuses = listOf("PENDING", "CONFIRMED")
        check(!bookings.existsOverlapping(carId, overlappingStatuses, request.startDate, request.endDate)) {
            "This car is already booked for the selected dates"
        }

        val days = ChronoUnit.DAYS.between(request.startDate, request.endDate)
        val total = car.pricePerDay.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP)
        val bookingId = "RNT-" + UUID.randomUUID().toString().replace("-", "").take(20).uppercase()
        val ledgerRef = "RENTAL:$bookingId"

        wallet.reserve(userId, total)
        wallet.finalizeReservedDebit(userId, total, ledgerRef, bookingId)

        val now = Instant.now()
        val saved = bookings.save(
            RentalBookingEntity(
                bookingId = bookingId,
                userId = userId,
                carId = carId,
                pickupLocation = pickup,
                dropLocation = drop,
                startDate = request.startDate,
                endDate = request.endDate,
                totalAmount = total,
                status = "CONFIRMED",
                walletLedgerRef = ledgerRef,
                createdAt = now,
                updatedAt = now
            )
        )
        return toBookingResponse(saved, car.name)
    }

    @Transactional
    fun cancelBooking(userId: Long, bookingId: String): RentalBookingResponse {
        val booking = bookings.findByBookingIdAndUserId(bookingId, userId)
            .orElseThrow { IllegalArgumentException("Rental booking not found") }
        check(booking.status == "CONFIRMED") { "Only confirmed bookings can be cancelled" }
        check(booking.startDate.isAfter(LocalDate.now())) { "Bookings starting today cannot be cancelled" }

        booking.status = "CANCELLED"
        booking.updatedAt = Instant.now()
        wallet.credit(
            userId = userId,
            amount = booking.totalAmount,
            externalRef = "RENTAL_REFUND:$bookingId",
            referenceType = "RENTAL_REFUND",
            referenceId = bookingId,
            description = "Car rental refund"
        )
        val car = cars.findById(booking.carId).orElse(null)
        bookings.save(booking)
        return toBookingResponse(booking, car?.name ?: "Car")
    }

    private fun toCarResponse(car: RentalCarEntity) = RentalCarResponse(
        id = requireNotNull(car.id).toString(),
        name = car.name,
        category = car.category,
        seats = car.seats,
        transmission = car.transmission,
        pricePerDay = car.pricePerDay.setScale(2)
    )

    private fun toBookingResponse(booking: RentalBookingEntity, carName: String) = RentalBookingResponse(
        bookingId = booking.bookingId,
        carName = carName,
        pickup = booking.pickupLocation,
        drop = booking.dropLocation,
        startDate = booking.startDate,
        endDate = booking.endDate,
        total = booking.totalAmount.setScale(2),
        status = booking.status,
        createdAt = booking.createdAt
    )
}
