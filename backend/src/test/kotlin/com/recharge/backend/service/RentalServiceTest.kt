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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

class RentalServiceTest {
    private val vendors = Mockito.mock(RentalVendorRepository::class.java)
    private val drivers = Mockito.mock(RentalDriverRepository::class.java)
    private val cars = Mockito.mock(RentalCarRepository::class.java)
    private val bookings = Mockito.mock(RentalBookingRepository::class.java)
    private val users = Mockito.mock(UserRepository::class.java)
    private val rentalPayments = Mockito.mock(RentalPaymentService::class.java)
    private val rentalPaymentRepository = Mockito.mock(RentalPaymentRepository::class.java)
    private val rentalPayouts = Mockito.mock(RentalPayoutService::class.java)
    private val vendorReviews = Mockito.mock(RentalVendorReviewRepository::class.java)
    private val carReviews = Mockito.mock(RentalCarReviewRepository::class.java)
    private val service = RentalService(vendors, drivers, cars, bookings, users, rentalPayments, rentalPaymentRepository, rentalPayouts, vendorReviews, carReviews)

    @Test
    fun bookingTotalIsCalculatedServerSideAndRentalPaymentIsUsed() {
        val car = RentalCarEntity(
            id = 7L, name = "Test Sedan", category = "Sedan", seats = 5,
            transmission = "Automatic", pricePerDay = BigDecimal("2000.00"), active = true, vendorId = 9L, driverId = 10L, approvalStatus = "APPROVED"
        )
        val start = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0)
        val end = start.plusDays(3)

        Mockito.doReturn(Optional.of(car))
            .`when`(cars)
            .findByIdForUpdate(7L)

        Mockito.doReturn(Optional.of(com.recharge.backend.domain.RentalDriverEntity(id = 10L, vendorId = 9L, fullName = "Driver", mobile = "9999999999", licenseNumber = "DL", licenseExpiry = end.plusDays(100))))
            .`when`(drivers).findById(10L)

        Mockito.doReturn(Optional.of(com.recharge.backend.domain.RentalVendorEntity(id = 9L, userId = 99L, fullName = "Vendor", address = "Address", city = "Darbhanga", state = "Bihar", pinCode = "846001")))
            .`when`(vendors).findById(9L)

        Mockito.doReturn(false)
            .`when`(bookings)
            .existsOverlapping(7L, listOf("PENDING", "CONFIRMED"), start, end)

        Mockito.doAnswer { invocation -> invocation.arguments[0] }
            .`when`(bookings)
            .save(any(RentalBookingEntity::class.java))

        val payment = com.recharge.backend.domain.RentalPaymentEntity(id = 21L, paymentId = "RNP-TEST", bookingId = "RNT-TEST", userId = 42L, amount = BigDecimal("6000.00"), method = "WALLET", status = "PAID", walletLedgerRef = "RENTAL:RNT-TEST")
        Mockito.doReturn(payment).`when`(rentalPayments).pay(Mockito.eq(42L), Mockito.anyString(), Mockito.eq(BigDecimal("6000.00")), Mockito.eq("WALLET"), Mockito.eq("client-1"))

        val result = service.createBooking(
            42L,
            RentalBookingRequest("client-1", "7", "Darbhanga", "Patna", start, end)
        )

        assertEquals(BigDecimal("6000.00"), result.total)
        assertEquals("CONFIRMED", result.status)
        Mockito.verify(rentalPayments, Mockito.times(1)).pay(42L, result.bookingId, BigDecimal("6000.00"), "WALLET", "client-1")
    }


    @Test
    fun vendorCannotBookOwnVehicle() {
        val car = RentalCarEntity(
            id = 8L, name = "Vendor Sedan", category = "Sedan", seats = 5,
            transmission = "Automatic", pricePerDay = BigDecimal("2000.00"), active = true,
            vendorId = 11L, driverId = 12L, approvalStatus = "APPROVED"
        )
        val start = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0)
        val end = start.plusDays(3)

        Mockito.doReturn(Optional.of(car)).`when`(cars).findByIdForUpdate(8L)
        Mockito.doReturn(Optional.of(com.recharge.backend.domain.RentalVendorEntity(
            id = 11L, userId = 42L, fullName = "Owner", address = "Address",
            city = "Darbhanga", state = "Bihar", pinCode = "846001"
        ))).`when`(vendors).findById(11L)

        assertThrows(IllegalArgumentException::class.java) {
            service.createBooking(
                42L,
                RentalBookingRequest("client-self", "8", "Darbhanga", "Patna", start, end)
            )
        }
        Mockito.verifyNoInteractions(rentalPayments)
    }
}