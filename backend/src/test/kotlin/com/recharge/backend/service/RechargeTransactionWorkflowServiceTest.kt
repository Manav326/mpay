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
    private val eventRepository = Mockito.mock(com.recharge.backend.repository.RechargeTransactionEventRepository::class.java)
    private val walletService = Mockito.mock(WalletService::class.java)
    private val clientCommissionService = Mockito.mock(ClientCommissionService::class.java)

    private val workflow = RechargeTransactionWorkflowService(
        repository,
        eventRepository,
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
        Mockito.doReturn(BigDecimal("1.00")).`when`(walletService).finalizeReservedDebit(
            userId = 2L,
            amount = BigDecimal("99.00"),
            externalRef = "RCH-200",
            referenceId = "RCH-200"
        )
        Mockito.doReturn(tx).`when`(repository).save(tx)

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
    fun preSubmissionFailureRecordsAnEventWithoutWalletMovement() {
        val request = RechargeRequestData("RCH-202", "client-202", "9955131155", null, "AIRTEL", "Bihar and Jharkhand")
        val plan = com.recharge.backend.provider.RechargePlan("PLAN-202", BigDecimal("349.00"), "28 days", "Test plan")
        Mockito.`when`(repository.findByClientRequestIdAndUserId("client-202", 2L)).thenReturn(Optional.empty())
        val saved = RechargeTransactionEntity(transactionId = "RCH-202", userId = 2L, status = "FAILED")
        Mockito.doReturn(saved).`when`(repository).save(Mockito.any(RechargeTransactionEntity::class.java))

        workflow.recordPreSubmissionFailure(
            2L,
            request,
            plan,
            "payu",
            BigDecimal("3.49"),
            BigDecimal("3.49"),
            BigDecimal("345.51"),
            "Recharge was not submitted: biller lookup failed"
        )

        Mockito.verify(walletService, Mockito.never()).reserve(Mockito.eq(2L), Mockito.any(BigDecimal::class.java))
        Mockito.verify(walletService, Mockito.never()).finalizeReservedDebit(Mockito.anyLong(), Mockito.any(BigDecimal::class.java), Mockito.anyString(), Mockito.anyString())
        Mockito.verify(eventRepository).save(Mockito.any(com.recharge.backend.domain.RechargeTransactionEventEntity::class.java))
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
        Mockito.doReturn(tx).`when`(repository).save(tx)

        workflow.applyProviderResult("RCH-201", "FAILED", "PROVIDER-201", "failed")

        Mockito.verify(walletService).releaseReservation(2L, BigDecimal("99.00"))
        Mockito.verify(clientCommissionService, Mockito.never()).creditUpstreamCommission(tx)
        assertEquals("FAILED", tx.status)
        Mockito.verify(repository).save(tx)
    }
}
