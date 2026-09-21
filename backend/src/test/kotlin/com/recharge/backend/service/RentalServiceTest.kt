package com.recharge.backend.service

import com.recharge.backend.domain.RentalCarEntity
import com.recharge.backend.domain.RentalBookingEntity
import com.recharge.backend.repository.RentalBookingRepository
import com.recharge.backend.repository.RentalCarRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class RentalServiceTest {
    private val cars = mockk<RentalCarRepository>()
    private val bookings = mockk<RentalBookingRepository>()
    private val wallet = mockk<WalletService>()
    private val service = RentalService(cars, bookings, wallet)

    @Test
    fun bookingTotalIsCalculatedServerSideAndWalletIsDebited() {
        val car = RentalCarEntity(
            id = 7L, name = "Test Sedan", category = "Sedan", seats = 5,
            transmission = "Automatic", pricePerDay = BigDecimal("2000.00"), active = true
        )
        every { cars.findById(7L) } returns Optional.of(car)
        every { bookings.existsOverlapping(7L, any(), any(), any()) } returns false
        every { wallet.reserve(42L, BigDecimal("6000.00")) } just Runs
        every { wallet.finalizeReservedDebit(42L, BigDecimal("6000.00"), any(), any()) } returns BigDecimal("1000.00")
        every { bookings.save(any()) } answers { firstArg<RentalBookingEntity>() }

        val result = service.createBooking(
            42L,
            com.recharge.backend.api.RentalBookingRequest(
                carId = "7",
                pickupLocation = "Darbhanga",
                dropLocation = "Patna",
                startDate = LocalDate.now().plusDays(2),
                endDate = LocalDate.now().plusDays(5)
            )
        )

        assertEquals(BigDecimal("6000.00"), result.total)
        assertEquals("CONFIRMED", result.status)
        verify { wallet.reserve(42L, BigDecimal("6000.00")) }
        verify { wallet.finalizeReservedDebit(42L, BigDecimal("6000.00"), any(), any()) }
    }
}
