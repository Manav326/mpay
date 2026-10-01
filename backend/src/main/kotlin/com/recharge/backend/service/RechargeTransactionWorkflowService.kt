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
    private val eventRepository: com.recharge.backend.repository.RechargeTransactionEventRepository,
    private val walletService: WalletService,
    private val clientCommissionService: ClientCommissionService
) {

    @Transactional
    fun reserve(
        userId: Long,
        request: RechargeRequestData,
        plan: RechargePlan,
        providerName: String,
        companyCommission: BigDecimal,
        clientCommission: BigDecimal,
        walletDebitAmount: BigDecimal,
        providerReference: String? = null
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
            providerReference = providerReference,
            providerOrderId = plan.providerOrderId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        walletService.reserve(userId, transaction.walletDebitAmount)
        val saved = repository.save(transaction)
        recordEvent(saved, null, saved.status, "RESERVED", "Recharge amount reserved before provider submission.")
        return saved
    }

    @Transactional
    fun recordPreSubmissionFailure(
        userId: Long,
        request: RechargeRequestData,
        plan: RechargePlan,
        providerName: String,
        companyCommission: BigDecimal,
        clientCommission: BigDecimal,
        walletDebitAmount: BigDecimal,
        message: String
    ): RechargeTransactionEntity {
        val existing = repository.findByClientRequestIdAndUserId(request.clientRequestId, userId).orElse(null)
        if (existing != null) return existing

        val now = Instant.now()
        val saved = repository.save(
            RechargeTransactionEntity(
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
                status = "FAILED",
                providerName = providerName,
                providerReference = null,
                providerOrderId = plan.providerOrderId,
                completedAt = now,
                message = message,
                createdAt = now,
                updatedAt = now
            )
        )
        recordEvent(saved, null, saved.status, "PRE_SUBMISSION_FAILURE", message)
        return saved
    }

    @Transactional
    fun applyProviderResult(
        transactionId: String,
        resultStatus: String,
        providerReference: String?,
        message: String?
    ): RechargeTransactionEntity {
        val tx = repository.findByTransactionIdForUpdate(transactionId).orElseThrow()
        val previousStatus = tx.status
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
                clientCommissionService.creditUpstreamCommission(tx)
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

        tx.providerReference = providerReference ?: tx.providerReference
        tx.message = message
        tx.updatedAt = Instant.now()
        val saved = repository.save(tx)
        recordEvent(saved, previousStatus, saved.status, "PROVIDER_RESULT_" + saved.status, message)
        return saved
    }

    @Transactional
    fun markProviderPending(transactionId: String, providerReference: String?, message: String?): RechargeTransactionEntity {
        val tx = repository.findByTransactionId(transactionId).orElseThrow()
        if (tx.status == "SUCCESS" || tx.status == "FAILED") return tx
        val previousStatus = tx.status
        tx.status = "PENDING"
        tx.providerReference = providerReference ?: tx.providerReference
        tx.message = message
        tx.updatedAt = Instant.now()
        val saved = repository.save(tx)
        recordEvent(saved, previousStatus, saved.status, "PROVIDER_CALL_INDETERMINATE", message)
        return saved
    }

    private fun recordEvent(tx: RechargeTransactionEntity, fromStatus: String?, toStatus: String, eventType: String, message: String?) {
        eventRepository.save(
            com.recharge.backend.domain.RechargeTransactionEventEntity(
                transactionId = tx.transactionId,
                fromStatus = fromStatus,
                toStatus = toStatus,
                eventType = eventType,
                providerReference = tx.providerReference,
                walletLedgerRef = tx.walletLedgerRef,
                walletAmount = tx.walletDebitAmount,
                message = message
            )
        )
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
