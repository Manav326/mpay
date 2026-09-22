package com.recharge.backend.service

import com.recharge.backend.domain.RentalPaymentEntity
import com.recharge.backend.repository.RentalPaymentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.util.Optional

class RentalPaymentServiceTest {
    private val payments = Mockito.mock(RentalPaymentRepository::class.java)
    private val wallet = Mockito.mock(WalletService::class.java)
    private val service = RentalPaymentService(payments, wallet)

    @Test
    fun walletPaymentCreatesPaidRentalPaymentAndUsesRentalReferenceType() {
        Mockito.doReturn(Optional.empty<RentalPaymentEntity>()).`when`(payments).findByUserIdAndClientRequestId(42L, "client-1")
        Mockito.doAnswer { it.arguments[0] as RentalPaymentEntity }.`when`(payments).save(Mockito.any(RentalPaymentEntity::class.java))

        val result = service.pay(42L, "RNT-123", BigDecimal("6000.00"), "WALLET", "client-1")

        assertEquals("PAID", result.status)
        assertEquals("WALLET", result.method)
        assertEquals("INTERNAL_WALLET", result.provider)
        assertEquals("RENTAL:RNT-123", result.walletLedgerRef)
        Mockito.verify(wallet).reserve(42L, BigDecimal("6000.00"))
        Mockito.verify(wallet).finalizeReservedDebit(
            42L, BigDecimal("6000.00"), "RENTAL:RNT-123", "RNT-123",
            "RENTAL_PAYMENT", "Car rental payment", "DEBIT"
        )
    }

    @Test
    fun duplicateClientRequestReturnsExistingPaymentWithoutSecondDebit() {
        val existing = RentalPaymentEntity(
            id = 5L, paymentId = "RNP-5", bookingId = "RNT-5", userId = 42L,
            amount = BigDecimal("6000.00"), method = "WALLET", status = "PAID"
        )
        Mockito.doReturn(Optional.of(existing)).`when`(payments).findByUserIdAndClientRequestId(42L, "client-1")

        val result = service.pay(42L, "RNT-5", BigDecimal("6000.00"), "WALLET", "client-1")

        assertEquals(existing, result)
        Mockito.verifyNoInteractions(wallet)
    }
}
