package com.recharge.backend.service

import com.recharge.backend.domain.WalletEntity
import com.recharge.backend.domain.WalletTransactionEntity
import com.recharge.backend.repository.WalletRepository
import com.recharge.backend.repository.WalletTransactionRepository
import org.springframework.data.domain.PageRequest
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class WalletService(
    private val wallets: WalletRepository,
    private val ledger: WalletTransactionRepository
) {
    fun getBalance(userId: Long): BigDecimal = wallets.findByUserId(userId).orElseThrow().balance

    fun getWalletSnapshot(userId: Long): WalletSnapshot {
        val wallet = wallets.findByUserId(userId).orElseThrow()
        return WalletSnapshot(
            balance = wallet.balance,
            reservedBalance = wallet.reservedBalance,
            availableBalance = wallet.balance.subtract(wallet.reservedBalance).max(BigDecimal.ZERO)
        )
    }

    @Transactional
    fun credit(
        userId: Long,
        amount: BigDecimal,
        externalRef: String = UUID.randomUUID().toString(),
        referenceType: String? = null,
        referenceId: String? = null,
        description: String? = null
    ): BigDecimal {
        require(amount.signum() > 0)
        if (ledger.existsByExternalRef(externalRef)) return getBalance(userId)
        val wallet = wallets.findByUserIdForUpdate(userId).orElseThrow()
        wallet.balance = wallet.balance.add(amount)
        wallets.save(wallet)
        ledger.save(
            WalletTransactionEntity(
                externalRef = externalRef,
                userId = userId,
                type = "CREDIT",
                amount = amount,
                status = "POSTED",
                referenceType = referenceType,
                referenceId = referenceId,
                description = description,
                createdAt = Instant.now()
            )
        )
        return wallet.balance
    }

    @Transactional
    fun reserve(userId: Long, amount: BigDecimal) {
        require(amount.signum() > 0)
        val wallet = wallets.findByUserIdForUpdate(userId).orElseThrow()
        val available = wallet.balance.subtract(wallet.reservedBalance)
        check(available >= amount) { "Insufficient wallet balance" }
        wallet.reservedBalance = wallet.reservedBalance.add(amount)
        wallets.save(wallet)
    }

    @Transactional
    fun finalizeReservedDebit(
        userId: Long,
        amount: BigDecimal,
        externalRef: String,
        referenceId: String? = null,
        referenceType: String = "RECHARGE",
        description: String = "Recharge debit",
        transactionType: String = "DEBIT"
    ): BigDecimal {
        require(amount.signum() > 0)
        if (ledger.existsByExternalRef(externalRef)) return getBalance(userId)

        val wallet = wallets.findByUserIdForUpdate(userId).orElseThrow()
        check(wallet.reservedBalance >= amount) { "Recharge reservation is missing or insufficient" }
        check(wallet.balance >= amount) { "Wallet balance is insufficient to finalize recharge" }

        wallet.reservedBalance = wallet.reservedBalance.subtract(amount)
        wallet.balance = wallet.balance.subtract(amount)
        wallets.save(wallet)

        ledger.save(
            WalletTransactionEntity(
                externalRef = externalRef,
                userId = userId,
                type = transactionType,
                amount = amount,
                status = "POSTED",
                referenceType = referenceType,
                referenceId = referenceId,
                description = description,
                createdAt = Instant.now()
            )
        )
        return wallet.balance
    }

    @Transactional
    fun releaseReservation(userId: Long, amount: BigDecimal) {
        require(amount.signum() > 0)
        val wallet = wallets.findByUserIdForUpdate(userId).orElseThrow()
        check(wallet.reservedBalance >= amount) { "Recharge reservation is missing or insufficient" }
        wallet.reservedBalance = wallet.reservedBalance.subtract(amount)
        wallets.save(wallet)
    }

    fun getAvailableBalance(userId: Long): BigDecimal = getWalletSnapshot(userId).availableBalance

    fun walletHistory(userId: Long, page: Int, size: Int, kind: String?, fromDate: LocalDate?, toDate: LocalDate?): WalletHistorySnapshot {
        require(page >= 0)
        require(size in 1..100)
        val now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
        val from = fromDate ?: now.toLocalDate()
        val to = (toDate ?: from).takeIf { !it.isBefore(from) } ?: throw IllegalArgumentException("toDate must be on or after fromDate")
        require(!to.isAfter(now.toLocalDate())) { "Future wallet history dates are not allowed" }
        val fromInstant = from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()
        val toExclusive = to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()
        val normalizedKind = kind?.trim()?.uppercase() ?: "ALL"
        val pageable = PageRequest.of(page, size)
        val pageData = when (normalizedKind) {
            "RECHARGE" -> ledger.findByUserIdAndReferenceTypeAndCreatedAtBetweenOrderByCreatedAtDesc(userId, "RECHARGE", fromInstant, toExclusive, pageable)
            "ADD_MONEY" -> ledger.findByUserIdAndReferenceTypeAndCreatedAtBetweenOrderByCreatedAtDesc(userId, "ADD_MONEY", fromInstant, toExclusive, pageable)
            "WITHDRAWAL", "WITHDRAW", "WITHDRAWN" -> ledger.findByUserIdAndReferenceTypeAndCreatedAtBetweenOrderByCreatedAtDesc(userId, "WITHDRAWAL", fromInstant, toExclusive, pageable)
            "RENTAL" -> ledger.findByUserIdAndReferenceTypeInAndCreatedAtBetweenOrderByCreatedAtDesc(
                userId, listOf("RENTAL_PAYMENT", "RENTAL_REFUND"), fromInstant, toExclusive, pageable
            )
            else -> ledger.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, fromInstant, toExclusive, pageable)
        }
        return WalletHistorySnapshot(pageData.content, pageData.number, pageData.size, pageData.totalElements, pageData.totalPages, pageData.hasNext(), from.toString(), to.toString())
    }
}

data class WalletHistorySnapshot(
    val items: List<WalletTransactionEntity>, val page: Int, val size: Int, val totalItems: Long,
    val totalPages: Int, val hasNext: Boolean, val fromDate: String, val toDate: String
)

data class WalletSnapshot(
    val balance: BigDecimal,
    val reservedBalance: BigDecimal,
    val availableBalance: BigDecimal
)
