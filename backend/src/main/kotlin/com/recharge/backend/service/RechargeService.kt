package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.provider.OperatorDetectionProvider
import com.recharge.backend.provider.ProviderRechargeRequest
import com.recharge.backend.provider.RechargeExecutionProvider
import com.recharge.backend.provider.RechargePlan
import com.recharge.backend.repository.RechargeTransactionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class RechargeService(
    private val operatorProviders: List<OperatorDetectionProvider>,
    private val offerService: RechargeOfferService,
    private val executionProviders: List<RechargeExecutionProvider>,
    private val workflow: RechargeTransactionWorkflowService,
    private val rechargeRepository: RechargeTransactionRepository,
    private val walletService: WalletService,
    @Value("\${app.commission.company-percent}") private val companyPercent: BigDecimal,
    private val commissionRateService: CommissionRateService,
    @Value("\${app.recharge.operator-providers:way2api,payu}") private val operatorProviderOrder: String,
    @Value("\${app.recharge.execution-providers:payu,mock}") private val executionProviderOrder: String
) {
    fun detect(request: OperatorCheckRequest): OperatorCheckResponse {
        var lastError: Exception? = null
        for (provider in orderedOperatorProviders()) {
            if (!provider.isConfigured()) continue
            try {
                val result = provider.detectOperator(request.mobileNumber)
                return OperatorCheckResponse(
                    mobileNumber = result.mobileNumber,
                    operator = result.operator,
                    providerOperator = result.providerOperator,
                    providerCircle = result.providerCircle,
                    circle = result.circle,
                    type = result.type,
                    providerOrderId = result.providerOrderId,
                    rechargeStatus = result.status,
                    pending = result.pending,
                    message = result.message,
                    providerMessageCode = result.messageCode
                )
            } catch (ex: Exception) {
                lastError = ex
            }
        }
        throw lastError ?: IllegalArgumentException("No configured operator detection provider is available")
    }

    fun plans(
        mobileNumber: String,
        operator: String,
        circle: String,
        providerOperator: String? = null,
        providerCircle: String? = null
    ): List<RechargePlanDto> =
        offerService.getOffers(mobileNumber, operator, circle, providerOperator, providerCircle)
            .map { plan ->
                RechargePlanDto(
                    id = plan.id,
                    amount = plan.amount,
                    validity = plan.validity,
                    description = plan.description
                )
            }

    fun createRechargePaymentOrder(userId: Long, request: RechargeRequest): CreatePaymentOrderRequest {
        val existingPlan = offerService.resolveCachedOffer(
            mobileNumber = request.mobileNumber,
            operator = request.operator,
            circle = request.circle,
            offerId = request.planId
        ) ?: throw IllegalArgumentException("Selected recharge offer has expired or is not available. Please refresh the offers and try again.")

        val amount = existingPlan.amount.setScale(2, RoundingMode.HALF_UP)
        val clientPercent = commissionRateService.rateForUser(userId)
        val clientCommission = amount.multiply(clientPercent).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
        val payable = amount.subtract(clientCommission).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        require(payable > BigDecimal.ZERO) { "Recharge payable amount must be greater than zero" }

        return CreatePaymentOrderRequest(
            amount = payable,
            clientRequestId = request.clientRequestId,
            purpose = "RECHARGE",
            rechargeMobileNumber = request.mobileNumber,
            rechargeOperator = request.operator.uppercase(),
            rechargeCircle = request.circle,
            rechargePlanId = request.planId
        )
    }

    fun recharge(userId: Long, request: RechargeRequest): RechargeResponse {
        val transactionId = "RTX-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().take(8)

        val existing = rechargeRepository.findByClientRequestIdAndUserId(request.clientRequestId, userId).orElse(null)
        if (existing != null) return toResponse(existing)

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
        val companyCommission = amount.multiply(companyPercent).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
        val clientCommission = amount.multiply(clientPercent).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
        val walletDebitAmount = amount.subtract(clientCommission).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

        val requestData = RechargeRequestData(
            transactionId = transactionId,
            clientRequestId = request.clientRequestId,
            mobileNumber = request.mobileNumber,
            operator = request.operator.uppercase(),
            circle = request.circle
        )

        val provider = resolveExecutionProvider(request.operator)
        val reserved = workflow.reserve(
            userId = userId,
            request = requestData,
            plan = selectedPlan.copy(amount = amount),
            providerName = provider.providerName,
            companyCommission = companyCommission,
            clientCommission = clientCommission,
            walletDebitAmount = walletDebitAmount
        )

        if (reserved.transactionId != transactionId) return toResponse(reserved)

        val providerResult = try {
            provider.recharge(
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
                message = "Recharge provider call did not complete: " + (ex.message ?: "unknown error")
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
            offerService.invalidate(request.mobileNumber, request.operator, request.circle)
        }

        return toResponse(finalized)
    }

    fun transaction(userId: Long, transactionId: String): RechargeTransactionStatusResponse {
        var tx = workflow.find(userId, transactionId)
        if (tx.status.equals("PENDING", ignoreCase = true)) {
            val provider = executionProviders.firstOrNull { it.providerName.equals(tx.providerName, ignoreCase = true) }
            val refreshed = provider?.getStatus(tx.providerReference ?: tx.providerOrderId ?: tx.transactionId)
            if (refreshed != null && !refreshed.status.equals("PENDING", ignoreCase = true)) {
                tx = workflow.applyProviderResult(
                    transactionId = tx.transactionId,
                    resultStatus = refreshed.status,
                    providerReference = refreshed.providerReference ?: tx.providerReference,
                    message = refreshed.message
                )
            }
        }

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

    private fun orderedOperatorProviders(): List<OperatorDetectionProvider> {
        val configuredNames = operatorProviderOrder.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val byName = operatorProviders.associateBy { it.providerName.lowercase() }
        val configured = configuredNames.mapNotNull { byName[it] }
        return configured + operatorProviders.filter { provider -> provider !in configured }
    }

    private fun resolveExecutionProvider(operator: String): RechargeExecutionProvider {
        val configuredNames = executionProviderOrder.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val byName = executionProviders.associateBy { it.providerName.lowercase() }
        for (name in configuredNames) {
            val provider = byName[name]
            if (provider != null && provider.supportsOperator(operator)) return provider
        }
        throw IllegalArgumentException("No configured recharge execution provider supports " + operator.uppercase())
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
