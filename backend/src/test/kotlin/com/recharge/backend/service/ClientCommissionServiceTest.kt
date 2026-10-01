package com.recharge.backend.service

import com.recharge.backend.domain.ClientCommissionSettingsEntity
import com.recharge.backend.domain.ClientReferralLinkEntity
import com.recharge.backend.domain.ClientUpstreamCommissionEntity
import com.recharge.backend.domain.RechargeTransactionEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.domain.WalletEntity
import com.recharge.backend.repository.ClientCommissionSettingsRepository
import com.recharge.backend.repository.ClientReferralLinkRepository
import com.recharge.backend.repository.ClientUpstreamCommissionRepository
import com.recharge.backend.repository.RechargeTransactionRepository
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.repository.WalletRepository
import com.recharge.backend.repository.WalletTransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.math.BigDecimal
import java.util.Optional

class ClientCommissionServiceTest {
    private val users = Mockito.mock(UserRepository::class.java)
    private val rechargeTransactions = Mockito.mock(RechargeTransactionRepository::class.java)
    private val referrals = Mockito.mock(ClientReferralLinkRepository::class.java)
    private val upstreamCommissions = Mockito.mock(ClientUpstreamCommissionRepository::class.java)
    private val settingsRepository = Mockito.mock(ClientCommissionSettingsRepository::class.java)
    private val commissionRates = Mockito.mock(CommissionRateService::class.java)
    private val walletRepository = Mockito.mock(WalletRepository::class.java)
    private val walletLedger = Mockito.mock(WalletTransactionRepository::class.java)
    private val wallet = WalletService(walletRepository, walletLedger)

    private val service = ClientCommissionService(
        users,
        rechargeTransactions,
        referrals,
        upstreamCommissions,
        settingsRepository,
        commissionRates,
        wallet
    )

    @Test
    fun upstreamCommissionUsesGrossRechargeAmountAndDoesNotChangeChildDebit() {
        val parent = UserEntity(id = 1L, publicId = "parent", role = "CLIENT", mobile = "9000000001")
        val settings = ClientCommissionSettingsEntity(
            id = 1L,
            level2DirectClientThreshold = 5,
            upstreamCommissionPercent = BigDecimal("0.1000"),
            upstreamCommissionActive = true
        )
        val recharge = RechargeTransactionEntity(
            transactionId = "RCH-100",
            userId = 2L,
            amount = BigDecimal("100.00"),
            walletDebitAmount = BigDecimal("99.00"),
            clientCommission = BigDecimal("1.00"),
            status = "SUCCESS"
        )

        Mockito.doReturn(Optional.of(ClientReferralLinkEntity(parentUserId = 1L, childUserId = 2L)))
            .`when`(referrals).findByChildUserId(2L)
        Mockito.doReturn(Optional.of(settings)).`when`(settingsRepository).findById(1L)
        Mockito.doReturn(5).`when`(referrals).countByParentUserId(1L)
        Mockito.doReturn(false).`when`(upstreamCommissions).existsByRechargeTransactionId("RCH-100")
        val parentWallet = WalletEntity(user = parent, balance = BigDecimal("100.00"))
        Mockito.doReturn(Optional.of(parentWallet)).`when`(walletRepository).findByUserIdForUpdate(1L)
        Mockito.doReturn(false).`when`(walletLedger).existsByExternalRef("UPSTREAM_COMMISSION:RCH-100")

        val result = service.creditUpstreamCommission(recharge)

        assertEquals(BigDecimal("100.10"), result)
        assertEquals(BigDecimal("100.10"), parentWallet.balance)
        val captor = ArgumentCaptor.forClass(ClientUpstreamCommissionEntity::class.java)
        Mockito.verify(upstreamCommissions).save(captor.capture())
        val audit = captor.value
        assertEquals(1L, audit.parentUserId)
        assertEquals(2L, audit.childUserId)
        assertEquals("RCH-100", audit.rechargeTransactionId)
        assertEquals(BigDecimal("100.00"), audit.rechargeAmount)
        assertEquals(BigDecimal("0.1000"), audit.commissionPercent)
        assertEquals(BigDecimal("0.10"), audit.commissionAmount)
        assertEquals("UPSTREAM_COMMISSION:RCH-100", audit.walletLedgerRef)
    }

    @Test
    fun upstreamCommissionIsNotCreditedBelowLevel2Threshold() {
        val settings = ClientCommissionSettingsEntity(
            id = 1L,
            level2DirectClientThreshold = 5,
            upstreamCommissionPercent = BigDecimal("0.1000"),
            upstreamCommissionActive = true
        )
        Mockito.doReturn(Optional.of(ClientReferralLinkEntity(parentUserId = 1L, childUserId = 2L)))
            .`when`(referrals).findByChildUserId(2L)
        Mockito.doReturn(Optional.of(settings)).`when`(settingsRepository).findById(1L)
        Mockito.doReturn(4).`when`(referrals).countByParentUserId(1L)

        val result = service.creditUpstreamCommission(
            RechargeTransactionEntity(
                transactionId = "RCH-101",
                userId = 2L,
                amount = BigDecimal("100.00"),
                walletDebitAmount = BigDecimal("99.00"),
                status = "SUCCESS"
            )
        )

        assertNull(result)
        Mockito.verifyNoInteractions(wallet)
        Mockito.verify(upstreamCommissions, Mockito.never()).save(Mockito.any(ClientUpstreamCommissionEntity::class.java))
    }
}
