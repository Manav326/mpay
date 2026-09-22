package com.recharge.backend.service

import com.recharge.backend.api.RentalBookingRequest
import com.recharge.backend.domain.RentalBookingEntity
import com.recharge.backend.domain.RentalCarEntity
import com.recharge.backend.repository.RentalBookingRepository
import com.recharge.backend.repository.RentalCarRepository
import com.recharge.backend.repository.RentalVendorRepository
import com.recharge.backend.repository.RentalDriverRepository
import com.recharge.backend.repository.RentalVendorReviewRepository
import com.recharge.backend.repository.RentalCarReviewRepository
import com.recharge.backend.repository.RentalPaymentRepository
import com.recharge.backend.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class RentalServiceTest {
    private val vendors = Mockito.mock(RentalVendorRepository::class.java)
    private val drivers = Mockito.mock(RentalDriverRepository::class.java)
    private val cars = Mockito.mock(RentalCarRepository::class.java)
    private val bookings = Mockito.mock(RentalBookingRepository::class.java)
    private val users = Mockito.mock(UserRepository::class.java)
    private val wallet = Mockito.mock(WalletService::class.java)
    private val rentalPayments = Mockito.mock(RentalPaymentService::class.java)
    private val rentalPaymentRepository = Mockito.mock(RentalPaymentRepository::class.java)
    private val vendorReviews = Mockito.mock(RentalVendorReviewRepository::class.java)
    private val carReviews = Mockito.mock(RentalCarReviewRepository::class.java)
    private val service = RentalService(vendors, drivers, cars, bookings, users, wallet, rentalPayments, rentalPaymentRepository, vendorReviews, carReviews)

    @Test
    fun bookingTotalIsCalculatedServerSideAndWalletIsDebited() {
        val car = RentalCarEntity(
            id = 7L, name = "Test Sedan", category = "Sedan", seats = 5,
            transmission = "Automatic", pricePerDay = BigDecimal("2000.00"), active = true, vendorId = 9L, driverId = 10L, approvalStatus = "APPROVED"
        )
        val start = LocalDate.now().plusDays(2)
        val end = start.plusDays(3)

        Mockito.doReturn(Optional.of(car))
            .`when`(cars)
            .findByIdForUpdate(7L)

        Mockito.doReturn(Optional.of(com.recharge.backend.domain.RentalDriverEntity(id = 10L, vendorId = 9L, fullName = "Driver", mobile = "9999999999", licenseNumber = "DL", licenseExpiry = end.plusDays(100))))
            .`when`(drivers).findById(10L)

        Mockito.doReturn(false)
            .`when`(bookings)
            .existsOverlapping(7L, listOf("PENDING", "CONFIRMED"), start, end)

        Mockito.doAnswer { invocation -> invocation.arguments[0] }
            .`when`(bookings)
            .save(any(RentalBookingEntity::class.java))

        val result = service.createBooking(
            42L,
            RentalBookingRequest("7", "Darbhanga", "Patna", start, end)
        )

        assertEquals(BigDecimal("6000.00"), result.total)
        assertEquals("CONFIRMED", result.status)
        Mockito.verify(wallet, Mockito.times(1)).reserve(42L, BigDecimal("6000.00"))
        Mockito.verify(wallet, Mockito.times(1)).finalizeReservedDebit(
            42L,
            BigDecimal("6000.00"),
            "RENTAL:" + result.bookingId,
            result.bookingId
        )
    }
}
