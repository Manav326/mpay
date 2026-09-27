package com.recharge.backend.service

import com.recharge.backend.domain.RechargeTransactionEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.RechargeTransactionRepository
import com.recharge.backend.repository.RoleHierarchyRepository
import com.recharge.backend.repository.RolePermissionRepository
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.repository.WalletTransactionRepository
import com.recharge.backend.repository.WalletWithdrawalRepository
import com.recharge.backend.api.RechargeTransactionStatusResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.util.Optional

class AdminFinancialServiceTest {

    @Test
    fun resolvesOnlyConfirmedPreSubmissionFailureAndReleasesReservation() {
        val recharges = mock(RechargeTransactionRepository::class.java)
        val withdrawals = mock(WalletWithdrawalRepository::class.java)
        val walletLedger = mock(WalletTransactionRepository::class.java)
        val users = mock(UserRepository::class.java)
        val roleAccess = mock(RoleAccessService::class.java)
        val rechargeService = mock(RechargeService::class.java)
        val walletService = mock(WalletService::class.java)

        val tx = RechargeTransactionEntity(
            transactionId = "RTX-HIST-001",
            clientRequestId = "client-001",
            userId = 4L,
            mobileNumber = "9955131155",
            operator = "AIRTEL",
            circle = "Bihar and Jharkhand",
            planId = "WAY2-ROFFER-TEST",
            amount = BigDecimal("26.00"),
            walletDebitAmount = BigDecimal("25.74"),
            status = "PENDING",
            providerName = "payu",
            providerReference = null,
            providerOrderId = "W2A-TEST",
            walletLedgerRef = null,
            message = "Recharge provider call did not complete: PayU biller/operator id is missing for recharge"
        )

        val viewer = UserEntity(id = 1L, role = "ADMIN")
        val target = UserEntity(id = 4L, role = "CLIENT")
        val expected = mock(RechargeTransactionStatusResponse::class.java)

        `when`(recharges.findByTransactionId(tx.transactionId)).thenReturn(Optional.of(tx))
        `when`(users.findById(4L)).thenReturn(Optional.of(target))
        `when`(walletLedger.existsByExternalRef(tx.transactionId)).thenReturn(false)
        `when`(rechargeService.transaction(4L, tx.transactionId)).thenReturn(expected)

        val service = AdminFinancialService(
            recharges = recharges,
            withdrawals = withdrawals,
            walletLedger = walletLedger,
            users = users,
            roleAccess = roleAccess,
            rechargeService = rechargeService,
            walletService = walletService
        )

        val actual = service.resolveConfirmedPreSubmissionFailure(viewer, tx.transactionId)

        assertSame(expected, actual)
        assertEquals("FAILED", tx.status)
        assertEquals(
            "Recharge provider call did not complete: PayU biller/operator id is missing for recharge " +
                "[Historical reconciliation: confirmed pre-submission failure; wallet reservation released.]",
            tx.message
        )
        verify(walletService).releaseReservation(4L, BigDecimal("25.74"))
        verify(recharges).save(tx)
    }

    @Test
    fun refusesTransactionsThatAlreadyHaveProviderReference() {
        val recharges = mock(RechargeTransactionRepository::class.java)
        val withdrawals = mock(WalletWithdrawalRepository::class.java)
        val walletLedger = mock(WalletTransactionRepository::class.java)
        val users = mock(UserRepository::class.java)
        val roleAccess = mock(RoleAccessService::class.java)
        val rechargeService = mock(RechargeService::class.java)
        val walletService = mock(WalletService::class.java)

        val tx = RechargeTransactionEntity(
            transactionId = "RTX-HIST-002",
            clientRequestId = "client-002",
            userId = 4L,
            status = "PENDING",
            providerName = "payu",
            providerReference = "PAYU-REF-123",
            walletDebitAmount = BigDecimal("100.00"),
            message = "Recharge provider call did not complete: PayU biller/operator id is missing for recharge"
        )

        val viewer = UserEntity(id = 1L, role = "ADMIN")
        val target = UserEntity(id = 4L, role = "CLIENT")

        `when`(recharges.findByTransactionId(tx.transactionId)).thenReturn(Optional.of(tx))
        `when`(users.findById(4L)).thenReturn(Optional.of(target))

        val service = AdminFinancialService(
            recharges = recharges,
            withdrawals = withdrawals,
            walletLedger = walletLedger,
            users = users,
            roleAccess = roleAccess,
            rechargeService = rechargeService,
            walletService = walletService
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.resolveConfirmedPreSubmissionFailure(viewer, tx.transactionId)
        }
        verifyNoInteractions(walletService)
    }
}
