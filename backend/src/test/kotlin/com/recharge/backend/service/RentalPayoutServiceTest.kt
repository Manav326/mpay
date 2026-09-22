package com.recharge.backend.service

import com.recharge.backend.domain.RentalBookingEntity
import com.recharge.backend.domain.RentalCarEntity
import com.recharge.backend.domain.RentalPayoutEntity
import com.recharge.backend.domain.RentalVendorEntity
import com.recharge.backend.repository.RentalBookingRepository
import com.recharge.backend.repository.RentalCarRepository
import com.recharge.backend.repository.RentalPayoutRepository
import com.recharge.backend.repository.RentalVendorRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

class RentalPayoutServiceTest {
    private val payouts = Mockito.mock(RentalPayoutRepository::class.java)
    private val cars = Mockito.mock(RentalCarRepository::class.java)
    private val vendors = Mockito.mock(RentalVendorRepository::class.java)
    private val wallet = Mockito.mock(WalletService::class.java)
    private val service = RentalPayoutService(payouts, bookings, cars, vendors, wallet, BigDecimal("10.00"))

    @Test
    fun completedBookingCreditsVendorNetAndRecordsPlatformFee() {
        val booking = RentalBookingEntity(bookingId = "RNT-1", userId = 42L, carId = 7L, totalAmount = BigDecimal("6000.00"), status = "COMPLETED", startDate = LocalDateTime.now().minusDays(3), endDate = LocalDateTime.now().minusDays(1))
        val car = RentalCarEntity(id = 7L, vendorId = 9L)
        val vendor = RentalVendorEntity(id = 9L, userId = 99L, fullName = "Vendor", address = "Address", city = "Patna", state = "Bihar", pinCode = "800001")
        Mockito.doReturn(Optional.of(booking)).`when`(bookings).findByBookingIdForUpdate("RNT-1")
        Mockito.doReturn(Optional.empty<RentalPayoutEntity>()).`when`(payouts).findByBookingId("RNT-1")
        Mockito.doReturn(Optional.of(car)).`when`(cars).findById(7L)
        Mockito.doReturn(Optional.of(vendor)).`when`(vendors).findById(9L)
        Mockito.doAnswer { it.arguments[0] as RentalPayoutEntity }.`when`(payouts).save(Mockito.any(RentalPayoutEntity::class.java))

        val result = service.settleCompletedBooking(booking)

        assertEquals(BigDecimal("6000.00"), result.grossAmount)
        assertEquals(BigDecimal("600.00"), result.platformFeeAmount)
        assertEquals(BigDecimal("5400.00"), result.vendorNetAmount)
        assertEquals("PAID", result.status)
        Mockito.verify(wallet).credit(99L, BigDecimal("5400.00"), requireNotNull(result.walletLedgerRef), "RENTAL_PAYOUT", "RNT-1", "Rental vendor payout")
    }

    @Test
    fun settlementSerializesOnBookingRowBeforeCreatingPayout() {
        val booking = RentalBookingEntity(
            bookingId = "RNT-LOCK",
            carId = 7L,
            totalAmount = BigDecimal("1000.00"),
            status = "COMPLETED"
        )
        Mockito.doReturn(Optional.of(booking)).`when`(bookings).findByBookingIdForUpdate("RNT-LOCK")
        Mockito.doReturn(Optional.empty<RentalPayoutEntity>()).`when`(payouts).findByBookingId("RNT-LOCK")
        Mockito.doReturn(Optional.of(RentalCarEntity(id = 7L, vendorId = 9L))).`when`(cars).findById(7L)
        Mockito.doReturn(Optional.of(RentalVendorEntity(id = 9L, userId = 99L, fullName = "Vendor", address = "Address", city = "Patna", state = "Bihar", pinCode = "800001"))).`when`(vendors).findById(9L)
        Mockito.doAnswer { it.arguments[0] as RentalPayoutEntity }.`when`(payouts).save(Mockito.any(RentalPayoutEntity::class.java))

        service.settleCompletedBooking(booking)

        Mockito.verify(bookings).findByBookingIdForUpdate("RNT-LOCK")
    }

    @Test
    fun repeatedSettlementDoesNotCreditVendorAgain() {
        val existing = RentalPayoutEntity(id = 1L, payoutId = "RNPY-1", bookingId = "RNT-1", vendorId = 9L, vendorUserId = 99L, grossAmount = BigDecimal("6000.00"), platformFeePercent = BigDecimal("10.00"), platformFeeAmount = BigDecimal("600.00"), vendorNetAmount = BigDecimal("5400.00"), status = "PAID")
        val booking = RentalBookingEntity(bookingId = "RNT-1", totalAmount = BigDecimal("6000.00"), status = "COMPLETED")
        Mockito.doReturn(Optional.of(booking)).`when`(bookings).findByBookingIdForUpdate("RNT-1")
        Mockito.doReturn(Optional.of(existing)).`when`(payouts).findByBookingId("RNT-1")

        val result = service.settleCompletedBooking(booking)

        assertEquals(existing, result)
        Mockito.verifyNoInteractions(wallet)
    }
}
