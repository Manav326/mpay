package com.recharge.backend.service

import com.recharge.backend.domain.RechargeTransactionEntity
import com.recharge.backend.provider.RechargePlan
import com.recharge.backend.repository.RechargeTransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@Service
class RechargeTransactionWorkflowService(
    private val repository: RechargeTransactionRepository,
    private val walletService: WalletService
) {

    @Transactional
    fun reserve(
        userId: Long,
        request: RechargeRequestData,
        plan: RechargePlan,
        providerName: String,
        companyCommission: BigDecimal,
        clientCommission: BigDecimal,
        walletDebitAmount: BigDecimal
    ): RechargeTransactionEntity {
        val existing = repository.findByClientRequestIdAndUserId(request.clientRequestId, userId).orElse(null)
        if (existing != null) return existing

        val transaction = RechargeTransactionEntity(
            transactionId = request.transactionId,
            clientRequestId = request.clientRequestId,
            userId = userId,
            mobileNumber = request.mobileNumber,
            recipientName = request.recipientName,
            operator = request.operator,
            circle = request.circle,
            planId = plan.id,
            planDescription = plan.description,
            planValidity = plan.validity,
            amount = plan.amount,
            companyCommission = companyCommission,
            clientCommission = clientCommission,
            walletDebitAmount = walletDebitAmount.max(BigDecimal.ZERO).setScale(2),
            status = "RESERVED",
            providerName = providerName,
            providerOrderId = plan.providerOrderId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        walletService.reserve(userId, transaction.walletDebitAmount)
        return repository.save(transaction)
    }

    @Transactional
    fun applyProviderResult(
        transactionId: String,
        resultStatus: String,
        providerReference: String?,
        message: String?
    ): RechargeTransactionEntity {
        val tx = repository.findByTransactionId(transactionId).orElseThrow()
        val status = resultStatus.uppercase()

        if (tx.status == "SUCCESS" || tx.status == "FAILED") return tx

        when (status) {
            "SUCCESS" -> {
                walletService.finalizeReservedDebit(
                    userId = tx.userId,
                    amount = tx.walletDebitAmount,
                    externalRef = tx.transactionId,
                    referenceId = tx.transactionId
                )
                tx.status = "SUCCESS"
                tx.walletLedgerRef = tx.transactionId
                tx.completedAt = Instant.now()
            }
            "FAILED" -> {
                walletService.releaseReservation(tx.userId, tx.walletDebitAmount)
                tx.status = "FAILED"
                tx.completedAt = Instant.now()
            }
            else -> {
                tx.status = "PENDING"
            }
        }

        tx.providerReference = providerReference
        tx.message = message
        tx.updatedAt = Instant.now()
        return repository.save(tx)
    }

    @Transactional
    fun markProviderPending(transactionId: String, providerReference: String?, message: String?): RechargeTransactionEntity {
        val tx = repository.findByTransactionId(transactionId).orElseThrow()
        if (tx.status == "SUCCESS" || tx.status == "FAILED") return tx
        tx.status = "PENDING"
        tx.providerReference = providerReference
        tx.message = message
        tx.updatedAt = Instant.now()
        return repository.save(tx)
    }

    fun find(userId: Long, transactionId: String): RechargeTransactionEntity {
        val tx = repository.findByTransactionId(transactionId).orElseThrow()
        check(tx.userId == userId) { "Recharge transaction does not belong to authenticated user" }
        return tx
    }
}

data class RechargeRequestData(
    val transactionId: String,
    val clientRequestId: String,
    val mobileNumber: String,
    val recipientName: String?,
    val operator: String,
    val circle: String
)
