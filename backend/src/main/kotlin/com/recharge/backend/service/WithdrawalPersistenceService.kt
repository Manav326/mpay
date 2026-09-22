package com.recharge.backend.service

import com.recharge.backend.domain.WalletWithdrawalEntity
import com.recharge.backend.repository.WalletWithdrawalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class WithdrawalPersistenceService(
    private val withdrawals: WalletWithdrawalRepository,
    private val wallet: WalletService
) {
    @Transactional
    fun createOrGetPending(
        userId: Long,
        amount: BigDecimal,
        upiId: String,
        providerName: String,
        clientRequestId: String
    ): WalletWithdrawalEntity {
        withdrawals.findByUserIdAndClientRequestId(userId, clientRequestId).orElse(null)?.let { return it }

        wallet.reserve(userId, amount)

        withdrawals.findByUserIdAndClientRequestId(userId, clientRequestId).orElse(null)?.let {
            wallet.releaseReservation(userId, amount)
            return it
        }

        return withdrawals.save(
            WalletWithdrawalEntity(
                withdrawalId = "WDR-" + UUID.randomUUID().toString().replace("-", "").take(24).uppercase(),
                clientRequestId = clientRequestId,
                userId = userId,
                amount = amount,
                upiId = upiId,
                providerName = providerName.lowercase(),
                status = "PENDING"
            )
        )
    }

    @Transactional
    fun markProcessing(
        withdrawalId: String,
        providerName: String,
        providerReference: String?,
        providerStatus: String?,
        message: String?
    ): WalletWithdrawalEntity {
        val entity = getLocked(withdrawalId, providerName)
        if (entity.status in TERMINAL_STATUSES) return entity
        entity.status = "PROCESSING"
        entity.providerReference = providerReference ?: entity.providerReference
        entity.providerStatus = providerStatus ?: entity.providerStatus
        entity.failureReason = null
        entity.walletLedgerRef = entity.walletLedgerRef ?: "WITHDRAWAL:" + entity.withdrawalId
        entity.updatedAt = Instant.now()
        return withdrawals.save(entity)
    }

    @Transactional
    fun markSucceeded(
        withdrawalId: String,
        providerName: String,
        providerReference: String?,
        providerStatus: String?,
        message: String?
    ): WalletWithdrawalEntity {
        val entity = getLocked(withdrawalId, providerName)
        if (entity.status == "SUCCESS") return entity
        if (entity.status in setOf("FAILED", "REVERSED")) return entity

        val ledgerRef = entity.walletLedgerRef ?: "WITHDRAWAL:" + entity.withdrawalId
        wallet.finalizeReservedDebit(
            userId = entity.userId,
            amount = entity.amount,
            externalRef = ledgerRef,
            referenceId = entity.withdrawalId,
            referenceType = "WITHDRAWAL",
            description = message?.takeIf { it.isNotBlank() } ?: "UPI withdrawal",
            transactionType = "WITHDRAW"
        )
        entity.status = "SUCCESS"
        entity.providerReference = providerReference ?: entity.providerReference
        entity.providerStatus = providerStatus ?: "PROCESSED"
        entity.walletLedgerRef = ledgerRef
        entity.failureReason = null
        entity.updatedAt = Instant.now()
        entity.completedAt = Instant.now()
        return withdrawals.save(entity)
    }

    @Transactional
    fun markFailed(
        withdrawalId: String,
        providerName: String,
        message: String
    ): WalletWithdrawalEntity {
        val entity = getLocked(withdrawalId, providerName)
        if (entity.status == "SUCCESS") return entity
        if (entity.status in setOf("FAILED", "REVERSED")) return entity

        wallet.releaseReservation(entity.userId, entity.amount)
        entity.status = "FAILED"
        entity.providerStatus = "FAILED"
        entity.failureReason = message.take(500)
        entity.updatedAt = Instant.now()
        entity.completedAt = Instant.now()
        return withdrawals.save(entity)
    }

    private fun getLocked(withdrawalId: String, providerName: String): WalletWithdrawalEntity =
        withdrawals.findByWithdrawalIdAndProviderName(withdrawalId, providerName.lowercase())
            .orElseThrow { IllegalArgumentException("Withdrawal not found") }

    companion object {
        private val TERMINAL_STATUSES = setOf("SUCCESS", "FAILED", "REVERSED")
    }
}
