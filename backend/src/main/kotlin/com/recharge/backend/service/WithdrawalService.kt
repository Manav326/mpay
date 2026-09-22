package com.recharge.backend.service

import com.recharge.backend.api.WithdrawMoneyResponse
import com.recharge.backend.config.MockWithdrawalProperties
import com.recharge.backend.api.WithdrawalHistoryItem
import com.recharge.backend.api.WithdrawalHistoryResponse
import com.recharge.backend.domain.WalletWithdrawalEntity
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.repository.WalletWithdrawalRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class WithdrawalService(
    private val withdrawals: WalletWithdrawalRepository,
    private val users: UserRepository,
    private val wallet: WalletService,
    private val providers: List<WithdrawalProvider>,
    private val properties: com.recharge.backend.config.WithdrawalProperties,
    private val persistence: WithdrawalPersistenceService
) {
    fun withdraw(
        userId: Long,
        amount: BigDecimal,
        providerName: String,
        clientRequestId: String,
        upiId: String
    ): WithdrawMoneyResponse {
        val normalizedAmount = amount.setScale(2)
        require(normalizedAmount >= BigDecimal("1.00")) { "Minimum withdrawal amount is ₹1" }

        val requestedProvider = providerName.trim().lowercase()
        val normalizedUpi = upiId.trim()
        val upiPattern = if (requestedProvider == "mock") {
            Regex("^[^\\s@]+@[^\\s@]+$")
        } else {
            Regex("^[A-Za-z0-9._-]+@[A-Za-z0-9._-]{2,}$")
        }
        require(upiPattern.matches(normalizedUpi)) { "Enter a valid UPI ID" }

        val normalizedRequestId = clientRequestId.trim()
        require(normalizedRequestId.isNotBlank()) { "Client request id is required" }
        require(normalizedRequestId.length <= 100) { "Client request id is too long" }

        val user = users.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

        val existing = withdrawals.findByUserIdAndClientRequestId(userId, normalizedRequestId)
        if (existing.isPresent) {
            val entity = existing.get()
            if (requestedProvider.isNotBlank() && !entity.providerName.equals(requestedProvider, true)) {
                throw IllegalArgumentException("Client request id already belongs to ${entity.providerName} withdrawal")
            }
            return responseFor(entity)
        }

        val provider = resolveProvider(requestedProvider)

        val saved = persistence.createOrGetPending(
            userId = userId,
            amount = normalizedAmount,
            upiId = normalizedUpi,
            providerName = provider.providerName,
            clientRequestId = normalizedRequestId
        )

        if (saved.status != "PENDING") {
            return responseFor(saved)
        }

        val result = try {
            provider.initiate(
                WithdrawalProviderRequest(
                    withdrawalId = saved.withdrawalId,
                    amount = saved.amount,
                    upiId = saved.upiId,
                    customerName = user.name?.trim()?.ifBlank { "mPay Customer" } ?: "mPay Customer",
                    customerEmail = user.email?.trim()?.ifBlank { user.mobile + "@mpay.local" }
                        ?: user.mobile + "@mpay.local",
                    customerMobile = user.mobile
                )
            )
        } catch (e: org.springframework.web.client.RestClientResponseException) {
            val providerMessage = e.responseBodyAsString
                .takeIf { it.isNotBlank() }
                ?.take(500)
                ?: e.message
                ?: "Provider request failed"

            if (e.statusCode.is4xxClientError) {
                val failed = persistence.markFailed(
                    saved.withdrawalId,
                    provider.providerName,
                    providerMessage
                )
                return responseFor(failed)
            }

            val processing = persistence.markProcessing(
                saved.withdrawalId,
                provider.providerName,
                providerReference = null,
                providerStatus = "UNKNOWN",
                message = "Provider outcome could not be confirmed: " + providerMessage
            )
            return responseFor(processing)
        } catch (e: Exception) {
            // A network/transport error does not prove the provider rejected the payout.
            // Keep the wallet reservation until a provider status/webhook resolves it.
            val processing = persistence.markProcessing(
                saved.withdrawalId,
                provider.providerName,
                providerReference = null,
                providerStatus = "UNKNOWN",
                message = "Provider outcome could not be confirmed: " + (e.message ?: "provider error")
            )
            return responseFor(processing)
        }

        val finalEntity = when (result.status.uppercase()) {
            "SUCCESS" -> persistence.markSucceeded(
                saved.withdrawalId,
                provider.providerName,
                result.providerReference,
                result.providerStatus ?: "PROCESSED",
                result.message
            )
            "FAILED", "REVERSED" -> persistence.markFailed(
                saved.withdrawalId,
                provider.providerName,
                result.message ?: "Payout failed"
            )
            else -> persistence.markProcessing(
                saved.withdrawalId,
                provider.providerName,
                result.providerReference,
                result.providerStatus ?: result.status,
                result.message
            )
        }

        return responseFor(finalEntity)
    }

    fun history(userId: Long, page: Int, size: Int): WithdrawalHistoryResponse {
        require(page >= 0) { "Page must be non-negative" }
        require(size in 1..50) { "Page size must be between 1 and 50" }
        val pageData = withdrawals.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
        return WithdrawalHistoryResponse(
            items = pageData.content.map {
                WithdrawalHistoryItem(
                    withdrawalId = it.withdrawalId,
                    clientRequestId = it.clientRequestId,
                    amount = it.amount.setScale(2),
                    upiId = it.upiId,
                    provider = it.providerName,
                    status = it.status,
                    providerReference = it.providerReference,
                    providerStatus = it.providerStatus,
                    failureReason = it.failureReason,
                    walletLedgerRef = it.walletLedgerRef,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    completedAt = it.completedAt
                )
            },
            page = pageData.number,
            size = pageData.size,
            totalItems = pageData.totalElements,
            totalPages = pageData.totalPages,
            hasNext = pageData.hasNext()
        )
    }

    fun get(userId: Long, withdrawalId: String): WithdrawMoneyResponse {
        val entity = withdrawals.findByWithdrawalId(withdrawalId.trim())
            .orElseThrow { IllegalArgumentException("Withdrawal not found") }
        require(entity.userId == userId) { "Withdrawal not found" }
        return responseFor(entity)
    }

    fun handleWebhook(
        providerName: String,
        merchantReference: String,
        status: String,
        providerReference: String?,
        providerStatus: String?,
        message: String?
    ) {
        val normalizedProvider = providerName.trim().lowercase()
        val normalizedReference = merchantReference.trim()
        require(normalizedReference.isNotBlank()) { "Withdrawal reference is required" }

        when (status.uppercase()) {
            "SUCCESS", "PROCESSED" -> persistence.markSucceeded(
                normalizedReference,
                normalizedProvider,
                providerReference,
                providerStatus,
                message
            )
            "FAILED", "REVERSED" -> persistence.markFailed(
                normalizedReference,
                normalizedProvider,
                message ?: "Payout failed"
            )
            else -> persistence.markProcessing(
                normalizedReference,
                normalizedProvider,
                providerReference,
                providerStatus ?: status,
                message
            )
        }
    }

    private fun resolveProvider(requestedName: String): WithdrawalProvider {
        val requested = requestedName.trim()
        if (requested.isNotBlank()) {
            return providers.firstOrNull {
                it.providerName.equals(requested, true) && it.isConfigured()
            } ?: throw ProviderNotConfiguredException("Payout provider is not configured: $requested")
        }

        properties.providerOrder
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { name ->
                providers.firstOrNull {
                    it.providerName.equals(name, true) && it.isConfigured()
                }?.let { return it }
            }

        throw ProviderNotConfiguredException("No configured payout provider is available")
    }

    private fun responseFor(entity: WalletWithdrawalEntity): WithdrawMoneyResponse {
        val snapshot = wallet.getWalletSnapshot(entity.userId)
        return WithdrawMoneyResponse(
            withdrawalId = entity.withdrawalId,
            status = entity.status,
            provider = entity.providerName,
            amount = entity.amount,
            upiId = entity.upiId,
            balance = snapshot.balance,
            availableBalance = snapshot.availableBalance,
            message = entity.failureReason ?: when (entity.status) {
                "SUCCESS" -> "Withdrawal completed successfully"
                "PROCESSING", "PENDING" -> "Withdrawal is being processed"
                "FAILED", "REVERSED" -> "Withdrawal failed"
                else -> null
            }
        )
    }
}


@Service
class MockWithdrawalProvider(
    private val properties: MockWithdrawalProperties
) : WithdrawalProvider {
    override val providerName: String = "mock"

    override fun isConfigured(): Boolean = properties.enabled

    override fun initiate(request: WithdrawalProviderRequest): WithdrawalProviderResult =
        WithdrawalProviderResult(
            status = "SUCCESS",
            providerReference = "mock_" + request.withdrawalId,
            providerStatus = "PROCESSED",
            message = "Mock withdrawal completed successfully"
        )
}
