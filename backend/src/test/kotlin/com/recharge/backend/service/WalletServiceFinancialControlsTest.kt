package com.recharge.backend.service

import com.recharge.backend.domain.WalletEntity
import com.recharge.backend.domain.WalletTransactionEntity
import com.recharge.backend.repository.WalletRepository
import com.recharge.backend.repository.WalletTransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.util.Optional

class WalletServiceFinancialControlsTest {
    private val wallets = Mockito.mock(WalletRepository::class.java)
    private val ledger = Mockito.mock(WalletTransactionRepository::class.java)
    private val service = WalletService(wallets, ledger)

    @Test
    fun reserveMovesFundsToReservedWithoutReducingBalance() {
        val wallet = WalletEntity(balance = BigDecimal("1000.00"), reservedBalance = BigDecimal("100.00"))
        Mockito.doReturn(Optional.of(wallet)).`when`(wallets).findByUserIdForUpdate(42L)

        service.reserve(42L, BigDecimal("250.00"))

        assertEquals(BigDecimal("1000.00"), wallet.balance)
        assertEquals(BigDecimal("350.00"), wallet.reservedBalance)
        Mockito.verify(wallets).save(wallet)
    }

    @Test
    fun reserveRejectsAmountAboveAvailableBalanceWithoutMutation() {
        val wallet = WalletEntity(balance = BigDecimal("1000.00"), reservedBalance = BigDecimal("800.00"))
        Mockito.doReturn(Optional.of(wallet)).`when`(wallets).findByUserIdForUpdate(42L)

        assertThrows(IllegalStateException::class.java) {
            service.reserve(42L, BigDecimal("250.00"))
        }

        assertEquals(BigDecimal("1000.00"), wallet.balance)
        assertEquals(BigDecimal("800.00"), wallet.reservedBalance)
        Mockito.verify(wallets, Mockito.never()).save(Mockito.any(WalletEntity::class.java))
    }

    @Test
    fun finalizeReservedDebitConsumesReservationAndPostsOneLedgerEntry() {
        val wallet = WalletEntity(balance = BigDecimal("1000.00"), reservedBalance = BigDecimal("250.00"))
        Mockito.doReturn(false).`when`(ledger).existsByExternalRef("WITHDRAWAL:WDR-1")
        Mockito.doReturn(Optional.of(wallet)).`when`(wallets).findByUserIdForUpdate(42L)

        val result = service.finalizeReservedDebit(
            userId = 42L,
            amount = BigDecimal("250.00"),
            externalRef = "WITHDRAWAL:WDR-1",
            referenceId = "WDR-1",
            referenceType = "WITHDRAWAL",
            description = "UPI withdrawal",
            transactionType = "WITHDRAW"
        )

        assertEquals(BigDecimal("750.00"), result)
        assertEquals(BigDecimal("750.00"), wallet.balance)
        assertEquals(BigDecimal("0.00"), wallet.reservedBalance)
        Mockito.verify(ledger).save(Mockito.argThat<WalletTransactionEntity> {
            externalRef == "WITHDRAWAL:WDR-1" &&
                userId == 42L &&
                type == "WITHDRAW" &&
                amount == BigDecimal("250.00") &&
                status == "POSTED" &&
                referenceType == "WITHDRAWAL" &&
                referenceId == "WDR-1"
        })
    }

    @Test
    fun releaseReservationRestoresAvailableFundsWithoutChangingBalance() {
        val wallet = WalletEntity(balance = BigDecimal("1000.00"), reservedBalance = BigDecimal("250.00"))
        Mockito.doReturn(Optional.of(wallet)).`when`(wallets).findByUserIdForUpdate(42L)

        service.releaseReservation(42L, BigDecimal("250.00"))

        assertEquals(BigDecimal("1000.00"), wallet.balance)
        assertEquals(BigDecimal("0.00"), wallet.reservedBalance)
        Mockito.verify(wallets).save(wallet)
    }
}