package com.recharge.backend.service

import com.recharge.backend.domain.RechargeTransactionEntity
import com.recharge.backend.repository.RechargeTransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.util.Optional

class RechargeTransactionWorkflowServiceTest {
    private val repository = Mockito.mock(RechargeTransactionRepository::class.java)
    private val walletService = Mockito.mock(WalletService::class.java)
    private val clientCommissionService = Mockito.mock(ClientCommissionService::class.java)

    private val workflow = RechargeTransactionWorkflowService(
        repository,
        walletService,
        clientCommissionService
    )

    @Test
    fun successfulProviderResultFinalizesNetDebitThenCreditsUpstream() {
        val tx = RechargeTransactionEntity(
            transactionId = "RCH-200",
            userId = 2L,
            amount = BigDecimal("100.00"),
            walletDebitAmount = BigDecimal("99.00"),
            clientCommission = BigDecimal("1.00"),
            status = "RESERVED"
        )
        Mockito.doReturn(Optional.of(tx)).`when`(repository).findByTransactionIdForUpdate("RCH-200")

        workflow.applyProviderResult("RCH-200", "SUCCESS", "PROVIDER-200", "ok")

        Mockito.verify(walletService).finalizeReservedDebit(
            userId = 2L,
            amount = BigDecimal("99.00"),
            externalRef = "RCH-200",
            referenceId = "RCH-200"
        )
        Mockito.verify(clientCommissionService).creditUpstreamCommission(tx)
        assertEquals("SUCCESS", tx.status)
        assertEquals("PROVIDER-200", tx.providerReference)
        Mockito.verify(repository).save(tx)
    }

    @Test
    fun failedProviderResultReleasesNetReservationAndDoesNotCreditUpstream() {
        val tx = RechargeTransactionEntity(
            transactionId = "RCH-201",
            userId = 2L,
            amount = BigDecimal("100.00"),
            walletDebitAmount = BigDecimal("99.00"),
            clientCommission = BigDecimal("1.00"),
            status = "RESERVED"
        )
        Mockito.doReturn(Optional.of(tx)).`when`(repository).findByTransactionIdForUpdate("RCH-201")

        workflow.applyProviderResult("RCH-201", "FAILED", "PROVIDER-201", "failed")

        Mockito.verify(walletService).releaseReservation(2L, BigDecimal("99.00"))
        Mockito.verify(clientCommissionService, Mockito.never()).creditUpstreamCommission(Mockito.any())
        assertEquals("FAILED", tx.status)
        Mockito.verify(repository).save(tx)
    }
}
