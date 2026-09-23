package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminFinancialService(
    private val recharges: RechargeTransactionRepository,
    private val withdrawals: WalletWithdrawalRepository,
    private val walletLedger: WalletTransactionRepository,
    private val users: UserRepository,
    private val roleAccess: RoleAccessService,
    private val rechargeService: RechargeService
) {
    fun recharges(viewer: UserEntity, page: Int, size: Int, status: String?, provider: String?): AdminFinancialRechargePageResponse {
        roleAccess.requirePermission(viewer, "VIEW_FINANCIAL_OPERATIONS")
        val result = rechargePage(page, size, status, provider)
        val userMap = users.findAllById(result.content.map { it.userId }.distinct()).associateBy { requireNotNull(it.id) }
        return AdminFinancialRechargePageResponse(
            items = result.content.map { tx ->
                val user = userMap[tx.userId]
                AdminFinancialRechargeOperation(
                    transactionId = tx.transactionId,
                    userPublicId = user?.publicId ?: tx.userId.toString(),
                    userName = user?.name ?: "User",
                    userMobile = user?.mobile ?: "—",
                    mobileNumber = tx.mobileNumber,
                    operator = tx.operator,
                    circle = tx.circle,
                    amount = tx.amount.setScale(2),
                    walletDebitAmount = tx.walletDebitAmount.setScale(2),
                    clientCommission = tx.clientCommission.setScale(2),
                    companyCommission = tx.companyCommission.setScale(2),
                    status = tx.status,
                    provider = tx.providerName,
                    providerReference = tx.providerReference,
                    providerOrderId = tx.providerOrderId,
                    walletLedgerRef = tx.walletLedgerRef,
                    message = tx.message,
                    createdAt = tx.createdAt,
                    updatedAt = tx.updatedAt
                )
            },
            page = result.number,
            size = result.size,
            totalItems = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext()
        )
    }

    fun withdrawals(viewer: UserEntity, page: Int, size: Int, status: String?, provider: String?): AdminFinancialWithdrawalPageResponse {
        roleAccess.requirePermission(viewer, "VIEW_FINANCIAL_OPERATIONS")
        val result = withdrawalPage(page, size, status, provider)
        val userMap = users.findAllById(result.content.map { it.userId }.distinct()).associateBy { requireNotNull(it.id) }
        return AdminFinancialWithdrawalPageResponse(
            items = result.content.map { tx ->
                val user = userMap[tx.userId]
                AdminFinancialWithdrawalOperation(
                    withdrawalId = tx.withdrawalId,
                    userPublicId = user?.publicId ?: tx.userId.toString(),
                    userName = user?.name ?: "User",
                    userMobile = user?.mobile ?: "—",
                    amount = tx.amount.setScale(2),
                    upiId = tx.upiId,
                    provider = tx.providerName,
                    status = tx.status,
                    providerReference = tx.providerReference,
                    providerStatus = tx.providerStatus,
                    failureReason = tx.failureReason,
                    walletLedgerRef = tx.walletLedgerRef,
                    createdAt = tx.createdAt,
                    updatedAt = tx.updatedAt,
                    completedAt = tx.completedAt
                )
            },
            page = result.number,
            size = result.size,
            totalItems = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext()
        )
    }

    fun walletHistory(viewer: UserEntity, page: Int, size: Int, referenceType: String?): AdminFinancialWalletPageResponse {
        roleAccess.requirePermission(viewer, "VIEW_FINANCIAL_OPERATIONS")
        require(page >= 0) { "Page must be non-negative" }
        require(size in 1..50) { "Page size must be between 1 and 50" }
        val normalized = referenceType?.trim()?.uppercase()?.takeIf { it.isNotBlank() && it != "ALL" }
        val pageable = PageRequest.of(page, size)
        val result = if (normalized == null) {
            walletLedger.findAllByOrderByCreatedAtDesc(pageable)
        } else {
            walletLedger.findAllByReferenceTypeOrderByCreatedAtDesc(normalized, pageable)
        }
        val userMap = users.findAllById(result.content.map { it.userId }.distinct()).associateBy { requireNotNull(it.id) }
        return AdminFinancialWalletPageResponse(
            items = result.content.map { tx ->
                val user = userMap[tx.userId]
                AdminFinancialWalletOperation(
                    id = requireNotNull(tx.id),
                    userPublicId = user?.publicId ?: tx.userId.toString(),
                    userName = user?.name ?: "User",
                    userMobile = user?.mobile ?: "—",
                    type = tx.type,
                    amount = tx.amount.setScale(2),
                    status = tx.status,
                    referenceType = tx.referenceType,
                    referenceId = tx.referenceId,
                    externalRef = tx.externalRef,
                    description = tx.description,
                    createdAt = tx.createdAt
                )
            },
            page = result.number,
            size = result.size,
            totalItems = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext()
        )
    }

    @Transactional
    fun refreshRecharge(viewer: UserEntity, transactionId: String): RechargeTransactionStatusResponse {
        roleAccess.requirePermission(viewer, "MANAGE_RECHARGE_OPERATIONS")
        val tx = recharges.findByTransactionId(transactionId)
            .orElseThrow { IllegalArgumentException("Recharge transaction not found") }
        return rechargeService.transaction(tx.userId, tx.transactionId)
    }

    private fun rechargePage(page: Int, size: Int, status: String?, provider: String?): Page<com.recharge.backend.domain.RechargeTransactionEntity> {
        require(page >= 0) { "Page must be non-negative" }
        require(size in 1..50) { "Page size must be between 1 and 50" }
        val pageable = PageRequest.of(page, size)
        val s = status?.trim()?.uppercase()?.takeIf { it.isNotBlank() && it != "ALL" }
        val p = provider?.trim()?.uppercase()?.takeIf { it.isNotBlank() && it != "ALL" }
        return when {
            s != null && p != null -> recharges.findAllByStatusAndProviderNameOrderByCreatedAtDesc(s, p, pageable)
            s != null -> recharges.findAllByStatusOrderByCreatedAtDesc(s, pageable)
            p != null -> recharges.findAllByProviderNameOrderByCreatedAtDesc(p, pageable)
            else -> recharges.findAllByOrderByCreatedAtDesc(pageable)
        }
    }

    private fun withdrawalPage(page: Int, size: Int, status: String?, provider: String?): Page<com.recharge.backend.domain.WalletWithdrawalEntity> {
        require(page >= 0) { "Page must be non-negative" }
        require(size in 1..50) { "Page size must be between 1 and 50" }
        val pageable = PageRequest.of(page, size)
        val s = status?.trim()?.uppercase()?.takeIf { it.isNotBlank() && it != "ALL" }
        val p = provider?.trim()?.uppercase()?.takeIf { it.isNotBlank() && it != "ALL" }
        return when {
            s != null && p != null -> withdrawals.findAllByStatusAndProviderNameOrderByCreatedAtDesc(s, p, pageable)
            s != null -> withdrawals.findAllByStatusOrderByCreatedAtDesc(s, pageable)
            p != null -> withdrawals.findAllByProviderNameOrderByCreatedAtDesc(p, pageable)
            else -> withdrawals.findAllByOrderByCreatedAtDesc(pageable)
        }
    }
}
