package com.recharge.backend.service

import com.recharge.backend.api.RentalBookingRequest
import com.recharge.backend.api.RentalVehicleUnavailabilityRequest
import com.recharge.backend.domain.RentalBookingEntity
import com.recharge.backend.domain.RentalCarEntity
import com.recharge.backend.repository.RentalBookingRepository
import com.recharge.backend.repository.RentalCarRepository
import com.recharge.backend.repository.RentalVendorRepository
import com.recharge.backend.repository.RentalDriverRepository
import com.recharge.backend.repository.RentalVendorReviewRepository
import com.recharge.backend.repository.RentalCarReviewRepository
import com.recharge.backend.repository.RentalVehicleUnavailabilityRepository
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
    private val vehicleUnavailability = Mockito.mock(RentalVehicleUnavailabilityRepository::class.java)
    private val users = Mockito.mock(UserRepository::class.java)
    private val rentalPayments = Mockito.mock(RentalPaymentService::class.java)
    private val rentalPaymentRepository = Mockito.mock(RentalPaymentRepository::class.java)
    private val rentalPayouts = Mockito.mock(RentalPayoutService::class.java)
    private val vendorReviews = Mockito.mock(RentalVendorReviewRepository::class.java)
    private val carReviews = Mockito.mock(RentalCarReviewRepository::class.java)
    private val service = RentalService(vendors, drivers, cars, bookings, vehicleUnavailability, users, rentalPayments, rentalPaymentRepository, rentalPayouts, vendorReviews, carReviews)

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
        Mockito.doReturn(payment).`when`(rentalPayments).pay(anyLongValue(), anyStringValue(), anyBigDecimalValue(), anyStringValue(), anyStringValue())

        val result = service.createBooking(
            42L,
            RentalBookingRequest("client-1", "7", "Darbhanga", "Patna", start, end)
        )

        assertEquals(BigDecimal("6000.00"), result.total)
        assertEquals("CONFIRMED", result.status)
        Mockito.verify(rentalPayments, Mockito.times(1)).pay(42L, result.bookingId, BigDecimal("6000.00"), "WALLET", "client-1")
    }


    private fun anyLongValue(): Long {
        Mockito.anyLong()
        return 0L
    }

    private fun anyStringValue(): String {
        Mockito.anyString()
        return ""
    }

    private fun anyBigDecimalValue(): BigDecimal {
        Mockito.any(BigDecimal::class.java)
        return BigDecimal.ZERO
    }

    @Test
    fun vendorCanTakeApprovedVehicleOffMarketWithReason() {
        val vendor = com.recharge.backend.domain.RentalVendorEntity(
            id = 9L, userId = 99L, status = "VERIFIED", fullName = "Vendor",
            address = "Address", city = "Patna", state = "Bihar", pinCode = "800001"
        )
        val car = RentalCarEntity(
            id = 7L, name = "Test Sedan", category = "Sedan", seats = 5,
            transmission = "Automatic", active = true, vendorId = 9L, driverId = 10L, approvalStatus = "APPROVED"
        )
        val start = LocalDate.now().plusDays(3)
        val end = start.plusDays(4)
        val saved = com.recharge.backend.domain.RentalVehicleUnavailabilityEntity(
            id = 55L, carId = 7L, vendorId = 9L, vendorUserId = 99L,
            startDate = start, endDate = end, reasonCode = "SERVICE_MAINTENANCE", reasonNote = "Routine service"
        )

        Mockito.doReturn(Optional.of(vendor)).`when`(vendors).findByUserId(99L)
        Mockito.doReturn(Optional.of(car)).`when`(cars).findByIdForUpdate(7L)
        Mockito.doReturn(false).`when`(bookings).existsOverlapping(
            7L, listOf("PENDING", "CONFIRMED"), start.atStartOfDay(), end.plusDays(1).atStartOfDay()
        )
        Mockito.doReturn(false).`when`(vehicleUnavailability).existsOverlapping(7L, start, end)
        Mockito.doReturn(saved).`when`(vehicleUnavailability).save(any(com.recharge.backend.domain.RentalVehicleUnavailabilityEntity::class.java))

        val result = service.takeVehicleOffMarket(
            99L, 7L,
            RentalVehicleUnavailabilityRequest(
                reasonCode = "SERVICE_MAINTENANCE", reasonNote = "Routine service", startDate = start, endDate = end
            )
        )

        assertEquals("55", result.id)
        assertEquals("SERVICE_MAINTENANCE", result.reasonCode)
        assertEquals(start, result.startDate)
        assertEquals(end, result.endDate)
        Mockito.verify(vehicleUnavailability).save(any(com.recharge.backend.domain.RentalVehicleUnavailabilityEntity::class.java))
    }

    @Test
    fun vendorCannotTakeVehicleOffMarketWhenBookingOverlaps() {
        val vendor = com.recharge.backend.domain.RentalVendorEntity(
            id = 9L, userId = 99L, status = "VERIFIED", fullName = "Vendor",
            address = "Address", city = "Patna", state = "Bihar", pinCode = "800001"
        )
        val car = RentalCarEntity(
            id = 7L, name = "Test Sedan", category = "Sedan", seats = 5,
            transmission = "Automatic", active = true, vendorId = 9L, driverId = 10L, approvalStatus = "APPROVED"
        )
        val start = LocalDate.now().plusDays(2)
        val end = start.plusDays(2)

        Mockito.doReturn(Optional.of(vendor)).`when`(vendors).findByUserId(99L)
        Mockito.doReturn(Optional.of(car)).`when`(cars).findByIdForUpdate(7L)
        Mockito.doReturn(true).`when`(bookings).existsOverlapping(
            7L, listOf("PENDING", "CONFIRMED"), start.atStartOfDay(), end.plusDays(1).atStartOfDay()
        )

        assertThrows(IllegalStateException::class.java) {
            service.takeVehicleOffMarket(
                99L, 7L,
                RentalVehicleUnavailabilityRequest(
                    reasonCode = "PRIVATE_USE",
                    startDate = start,
                    endDate = end
                )
            )
        }
        Mockito.verify(cars).findByIdForUpdate(7L)
        Mockito.verify(vehicleUnavailability, Mockito.never()).save(any(com.recharge.backend.domain.RentalVehicleUnavailabilityEntity::class.java))
    }
    @Test
    fun completingBookingLocksItAndSettlesPayoutOnlyOnce() {
        val booking = RentalBookingEntity(
            bookingId = "RNT-COMPLETE",
            userId = 42L,
            carId = 7L,
            totalAmount = BigDecimal("6000.00"),
            status = "CONFIRMED",
            startDate = LocalDateTime.now().minusDays(4),
            endDate = LocalDateTime.now().minusHours(1)
        )
        val car = RentalCarEntity(id = 7L, name = "Test Sedan", vendorId = 9L, driverId = 10L)
        val completedPayout = com.recharge.backend.domain.RentalPayoutEntity(
            payoutId = "RNPY-1",
            bookingId = "RNT-COMPLETE",
            vendorId = 9L,
            vendorUserId = 99L,
            grossAmount = BigDecimal("6000.00"),
            platformFeePercent = BigDecimal("10.00"),
            platformFeeAmount = BigDecimal("600.00"),
            vendorNetAmount = BigDecimal("5400.00"),
            status = "PAID"
        )

        Mockito.doReturn(Optional.of(booking)).`when`(bookings).findByBookingIdForUpdate("RNT-COMPLETE")
        Mockito.doReturn(Optional.of(car)).`when`(cars).findById(7L)
        Mockito.doReturn(Optional.empty<com.recharge.backend.domain.RentalDriverEntity>()).`when`(drivers).findById(10L)
        Mockito.doReturn(booking).`when`(bookings).save(Mockito.any(RentalBookingEntity::class.java))
        Mockito.doReturn(completedPayout).`when`(rentalPayouts).settleCompletedBooking(booking)

        val first = service.completeBooking("RNT-COMPLETE", 1L)

        assertEquals("COMPLETED", first.status)
        Mockito.verify(bookings).findByBookingIdForUpdate("RNT-COMPLETE")
        Mockito.verify(rentalPayouts).settleCompletedBooking(booking)

        assertThrows(IllegalStateException::class.java) {
            service.completeBooking("RNT-COMPLETE", 1L)
        }
        Mockito.verify(rentalPayouts, Mockito.times(1)).settleCompletedBooking(booking)
    }

    @Test
    fun cancellingBookingLocksStateAndRefundsOnlyOnce() {
        val booking = RentalBookingEntity(
            bookingId = "RNT-CANCEL",
            userId = 42L,
            carId = 7L,
            totalAmount = BigDecimal("2500.00"),
            status = "CONFIRMED",
            startDate = LocalDateTime.now().plusDays(2),
            endDate = LocalDateTime.now().plusDays(3)
        )
        val payment = com.recharge.backend.domain.RentalPaymentEntity(
            id = 31L,
            paymentId = "RNP-CANCEL",
            bookingId = "RNT-CANCEL",
            userId = 42L,
            amount = BigDecimal("2500.00"),
            method = "WALLET",
            status = "PAID",
            walletLedgerRef = "RENTAL:RNT-CANCEL"
        )

        Mockito.doReturn(Optional.of(booking)).`when`(bookings).findByBookingIdForUpdate("RNT-CANCEL")
        Mockito.doReturn(Optional.of(payment)).`when`(rentalPaymentRepository).findByBookingIdAndUserId("RNT-CANCEL", 42L)
        Mockito.doReturn(payment).`when`(rentalPayments).refund(payment)
        Mockito.doReturn(Optional.empty<com.recharge.backend.domain.RentalCarEntity>()).`when`(cars).findById(7L)
        Mockito.doReturn(booking).`when`(bookings).save(Mockito.any(RentalBookingEntity::class.java))

        val first = service.cancelBooking(42L, "RNT-CANCEL")

        assertEquals("CANCELLED", first.status)
        Mockito.verify(bookings).findByBookingIdForUpdate("RNT-CANCEL")
        Mockito.verify(rentalPayments, Mockito.times(1)).refund(payment)

        assertThrows(IllegalStateException::class.java) {
            service.cancelBooking(42L, "RNT-CANCEL")
        }
        Mockito.verify(rentalPayments, Mockito.times(1)).refund(payment)
    }
    @Test
    fun createBookingRechecksIdempotencyAfterCarLock() {
        val start = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0)
        val end = start.plusDays(1)
        val car = RentalCarEntity(
            id = 30L, name = "Test MPV", category = "MPV", seats = 6, transmission = "Automatic",
            pricePerDay = BigDecimal("1800.00"), active = true, vendorId = 90L, driverId = 91L, approvalStatus = "APPROVED"
        )
        val existingBooking = RentalBookingEntity(
            bookingId = "RNT-EXISTING", userId = 42L, carId = 30L,
            pickupLocation = "Patna", dropLocation = "Gaya", startDate = start, endDate = end,
            totalAmount = BigDecimal("1800.00"), status = "CONFIRMED"
        )
        val existingPayment = com.recharge.backend.domain.RentalPaymentEntity(
            id = 41L, paymentId = "RNP-EXISTING", bookingId = "RNT-EXISTING", userId = 42L,
            amount = BigDecimal("1800.00"), method = "WALLET", status = "PAID",
            clientRequestId = "client-race", walletLedgerRef = "RENTAL:RNT-EXISTING"
        )

        Mockito.doReturn(Optional.empty<com.recharge.backend.domain.RentalPaymentEntity>())
            .doReturn(Optional.of(existingPayment)).`when`(rentalPaymentRepository)
            .findByUserIdAndClientRequestId(42L, "client-race")
        Mockito.doReturn(Optional.of(car)).`when`(cars).findByIdForUpdate(30L)
        Mockito.doReturn(Optional.of(existingBooking)).`when`(bookings).findByBookingIdAndUserId("RNT-EXISTING", 42L)
        Mockito.doReturn(Optional.of(car)).`when`(cars).findById(30L)
        Mockito.doReturn(Optional.empty<com.recharge.backend.domain.RentalDriverEntity>()).`when`(drivers).findById(91L)

        val result = service.createBooking(
            42L,
            RentalBookingRequest("client-race", "30", "Patna", "Gaya", start, end)
        )

        assertEquals("RNT-EXISTING", result.bookingId)
        assertEquals(BigDecimal("1800.00"), result.total)
        Mockito.verifyNoInteractions(rentalPayments)
        Mockito.verify(bookings, Mockito.never()).save(Mockito.any(RentalBookingEntity::class.java))
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

    @Test
    fun quoteRoundsPartialDayUpWhenBookingUsesDatetime() {
        val start = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0)
        val end = start.plusHours(25)
        val car = RentalCarEntity(id = 21L, name = "Test SUV", category = "SUV", seats = 5, transmission = "Automatic", pricePerDay = BigDecimal("1500.00"), active = true, vendorId = 31L, driverId = 32L, approvalStatus = "APPROVED")
        Mockito.doReturn(Optional.of(car)).`when`(cars).findById(21L)
        Mockito.doReturn(Optional.of(com.recharge.backend.domain.RentalVendorEntity(id = 31L, userId = 88L, fullName = "Vendor", address = "Address", city = "Patna", state = "Bihar", pinCode = "800001"))).`when`(vendors).findById(31L)
        Mockito.doReturn(Optional.of(com.recharge.backend.domain.RentalDriverEntity(id = 32L, vendorId = 31L, fullName = "Driver", mobile = "9999999999", licenseNumber = "DL", licenseExpiry = end.plusDays(100)))).`when`(drivers).findById(32L)
        Mockito.doReturn(false).`when`(bookings).existsOverlapping(21L, listOf("PENDING", "CONFIRMED"), start, end)

        val result = service.quoteBooking(42L, com.recharge.backend.api.RentalBookingQuoteRequest("21", "Patna", "Gaya", start, end))

        assertEquals(2L, result.days)
        assertEquals(BigDecimal("3000.00"), result.total)
    }
}