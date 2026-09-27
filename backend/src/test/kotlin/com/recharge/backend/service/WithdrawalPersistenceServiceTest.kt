package com.recharge.backend.service

import com.recharge.backend.domain.WalletWithdrawalEntity
import com.recharge.backend.repository.WalletWithdrawalRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals

class WithdrawalPersistenceServiceTest {

    private val withdrawals = Mockito.mock(WalletWithdrawalRepository::class.java)
    private val wallet = Mockito.mock(WalletService::class.java)
    private val service = WithdrawalPersistenceService(withdrawals, wallet)

    @Test
    fun createOrGetPendingUsesLockedLookupInsidePersistenceBoundary() {
        val pending = WalletWithdrawalEntity(
            withdrawalId = "WDR-TEST",
            clientRequestId = "REQ-TEST",
            userId = 42L,
            amount = BigDecimal("10.00"),
            upiId = "user@upi",
            providerName = "mock",
            status = "PENDING"
        )

        Mockito.doReturn(Optional.empty<WalletWithdrawalEntity>())
            .`when`(withdrawals).findLockedByUserIdAndClientRequestId(42L, "REQ-TEST")
        Mockito.doReturn(pending)
            .`when`(withdrawals).save(Mockito.any(WalletWithdrawalEntity::class.java))

        val result = service.createOrGetPending(
            userId = 42L,
            amount = BigDecimal("10.00"),
            upiId = "user@upi",
            providerName = "mock",
            clientRequestId = "REQ-TEST"
        )

        assertEquals("WDR-TEST", result.withdrawalId)
        Mockito.verify(withdrawals, Mockito.times(2))
            .findLockedByUserIdAndClientRequestId(42L, "REQ-TEST")
        Mockito.verify(withdrawals, Mockito.never())
            .findByUserIdAndClientRequestId(42L, "REQ-TEST")
        Mockito.verify(wallet).reserve(42L, BigDecimal("10.00"))
    }
}