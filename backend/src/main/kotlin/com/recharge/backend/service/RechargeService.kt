package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.provider.*
import com.recharge.backend.repository.RechargeTransactionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class RechargeService(
    private val operatorProvider: RechargeProvider,
    private val offerService: RechargeOfferService,
    private val executionProvider: RechargeExecutionProvider,
    private val workflow: RechargeTransactionWorkflowService,
    private val rechargeRepository: RechargeTransactionRepository,
    private val walletService: WalletService,
    @Value("\${app.commission.company-percent}") private val companyPercent: BigDecimal,
    private val commissionRateService: CommissionRateService
) {
    fun detect(request: OperatorCheckRequest): OperatorCheckResponse {
        val r = operatorProvider.detectOperator(request.mobileNumber)
        return OperatorCheckResponse(
            mobileNumber = r.mobileNumber,
            operator = r.operator,
            providerOperator = r.providerOperator,
            circle = r.circle,
            type = r.type,
            providerOrderId = r.providerOrderId,
            rechargeStatus = "UNKNOWN"
        )
    }

    fun plans(mobileNumber: String, operator: String, circle: String): List<RechargePlanDto> =
        offerService.getOffers(mobileNumber, operator, circle)
            .map {
                RechargePlanDto(
                    id = it.id,
                    amount = it.amount,
                    validity = it.validity,
                    description = it.description
                )
            }

    fun recharge(userId: Long, request: RechargeRequest): RechargeResponse {
        val transactionId = "RTX-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"

        val existing = rechargeRepository.findByClientRequestIdAndUserId(request.clientRequestId, userId).orElse(null)
        if (existing != null) return toResponse(existing)

        // Checkout never calls Way2API. The selected offer must already exist in our
        // short-lived server-side offer cache populated by /recharge/plans.
        val selectedPlan = offerService.resolveCachedOffer(
            mobileNumber = request.mobileNumber,
            operator = request.operator,
            circle = request.circle,
            offerId = request.planId
        ) ?: throw IllegalArgumentException(
            "Selected recharge offer has expired or is not available. Please refresh the offers and try again."
        )

        val amount = selectedPlan.amount.setScale(2, RoundingMode.HALF_UP)
        val clientPercent = commissionRateService.rateForUser(userId)
        val companyCommission = amount
            .multiply(companyPercent)
            .divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
        val clientCommission = amount
            .multiply(clientPercent)
            .divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
        val walletDebitAmount = amount.subtract(clientCommission).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

        val requestData = RechargeRequestData(
            transactionId = transactionId,
            clientRequestId = request.clientRequestId,
            mobileNumber = request.mobileNumber,
            operator = request.operator.uppercase(),
            circle = request.circle
        )

        val reserved = workflow.reserve(
            userId = userId,
            request = requestData,
            plan = selectedPlan.copy(amount = amount),
            providerName = executionProvider.providerName,
            companyCommission = companyCommission,
            clientCommission = clientCommission,
            walletDebitAmount = walletDebitAmount
        )

        if (reserved.transactionId != transactionId) {
            return toResponse(reserved)
        }

        val providerResult = try {
            executionProvider.recharge(
                ProviderRechargeRequest(
                    transactionId = reserved.transactionId,
                    mobileNumber = request.mobileNumber,
                    operator = request.operator.uppercase(),
                    circle = request.circle,
                    plan = selectedPlan.copy(amount = amount)
                )
            )
        } catch (ex: Exception) {
            workflow.markProviderPending(
                transactionId = reserved.transactionId,
                providerReference = null,
                message = "Recharge provider call did not complete: ${ex.message ?: "unknown error"}"
            )
            return toResponse(workflow.find(userId, reserved.transactionId))
        }

        val finalized = workflow.applyProviderResult(
            transactionId = reserved.transactionId,
            resultStatus = providerResult.status,
            providerReference = providerResult.providerReference,
            message = providerResult.message
        )

        if (providerResult.status.equals("FAILED", ignoreCase = true)) {
            // R-Offers are number-specific and can change upstream.
            // Invalidate the cached set after a failed execution so the next
            // attempt must re-fetch a fresh set from Way2API.
            offerService.invalidate(
                mobileNumber = request.mobileNumber,
                operator = request.operator,
                circle = request.circle
            )
        }

        return toResponse(finalized)
    }

    fun transaction(userId: Long, transactionId: String): RechargeTransactionStatusResponse {
        val tx = workflow.find(userId, transactionId)
        val wallet = walletService.getWalletSnapshot(userId)
        return RechargeTransactionStatusResponse(
            transactionId = tx.transactionId,
            clientRequestId = tx.clientRequestId,
            mobileNumber = tx.mobileNumber,
            operator = tx.operator,
            circle = tx.circle,
            planId = tx.planId,
            planDescription = tx.planDescription,
            planValidity = tx.planValidity,
            amount = tx.amount,
            walletDebitAmount = tx.walletDebitAmount,
            status = tx.status,
            provider = tx.providerName,
            providerReference = tx.providerReference,
            providerOrderId = tx.providerOrderId,
            walletLedgerRef = tx.walletLedgerRef,
            completedAt = tx.completedAt,
            clientCommission = tx.clientCommission,
            companyCommission = tx.companyCommission,
            message = tx.message,
            walletBalance = wallet.balance,
            walletAvailableBalance = wallet.availableBalance
        )
    }

    private fun toResponse(tx: com.recharge.backend.domain.RechargeTransactionEntity): RechargeResponse {
        val wallet = walletService.getWalletSnapshot(tx.userId)
        return RechargeResponse(
            transactionId = tx.transactionId,
            status = tx.status,
            amount = tx.amount,
            commission = tx.clientCommission,
            walletDebitAmount = tx.walletDebitAmount,
            walletBalance = wallet.balance,
            walletAvailableBalance = wallet.availableBalance
        )
    }
}
