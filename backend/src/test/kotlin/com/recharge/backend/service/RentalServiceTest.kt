package com.recharge.backend.service

import com.recharge.backend.api.RentalBookingRequest
import com.recharge.backend.domain.RentalBookingEntity
import com.recharge.backend.domain.RentalCarEntity
import com.recharge.backend.repository.RentalBookingRepository
import com.recharge.backend.repository.RentalCarRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class RentalServiceTest {
    private val cars = Mockito.mock(RentalCarRepository::class.java)
    private val bookings = Mockito.mock(RentalBookingRepository::class.java)
    private val wallet = Mockito.mock(WalletService::class.java)
    private val service = RentalService(cars, bookings, wallet)

    @Test
    fun bookingTotalIsCalculatedServerSideAndWalletIsDebited() {
        val car = RentalCarEntity(
            id = 7L, name = "Test Sedan", category = "Sedan", seats = 5,
            transmission = "Automatic", pricePerDay = BigDecimal("2000.00"), active = true
        )
        when(cars.findByIdForUpdate(7L)).thenReturn(Optional.of(car))
        when(bookings.existsOverlapping(Mockito.eq(7L), any(), any(), any())).thenReturn(false)
        when(
            wallet.finalizeReservedDebit(
                Mockito.eq(42L),
                Mockito.eq(BigDecimal("6000.00")),
                any(),
                any()
            )
        ).thenReturn(BigDecimal("1000.00"))
        when(bookings.save(any(RentalBookingEntity::class.java))).thenAnswer { it.arguments[0] }

        val start = LocalDate.now().plusDays(2)
        val result = service.createBooking(
            42L,
            RentalBookingRequest("7", "Darbhanga", "Patna", start, start.plusDays(3))
        )

        assertEquals(BigDecimal("6000.00"), result.total)
        assertEquals("CONFIRMED", result.status)
        verify(wallet, times(1)).reserve(42L, BigDecimal("6000.00"))
        verify(
            wallet,
            times(1)
        ).finalizeReservedDebit(Mockito.eq(42L), Mockito.eq(BigDecimal("6000.00")), any(), any())
    }
}
