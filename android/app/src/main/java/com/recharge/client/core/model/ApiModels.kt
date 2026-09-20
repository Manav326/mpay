package com.recharge.client.core.model

import java.math.BigDecimal

/** Mirrors the backend authentication and client DTOs. */
data class LoginRequest(val mobile: String, val password: String)
data class RegisterRequest(val name: String?, val email: String?, val mobile: String, val password: String)
data class ForgotPasswordRequest(val mobile: String)
data class ForgotPasswordResponse(val status: String, val expiresInSeconds: Long, val demoOtp: String? = null, val deliveryMode: String = "twilio")
data class ResetPasswordRequest(val mobile: String, val otp: String, val newPassword: String)
data class ResetPasswordResponse(val status: String)

data class RefreshTokenRequest(val refreshToken: String)
data class LoginResponse(val accessToken: String, val refreshToken: String, val userId: Long, val role: String)
data class CurrentUserResponse(
    val userId: Long,
    val publicUserId: String,
    val mobile: String,
    val name: String? = null,
    val email: String? = null,
    val profileImageUrl: String? = null,
    val profileImageVersion: Long? = null,
    val role: String,
    val commissionRate: BigDecimal = BigDecimal.ZERO,
    val createdAt: String? = null,
    val profileUpdatedAt: String? = null
)

data class ProfileUpdateRequest(
    val name: String?,
    val email: String?
)
data class WalletResponse(
    val balance: BigDecimal,
    val availableBalance: BigDecimal? = null,
    val reservedBalance: BigDecimal? = null,
    val currency: String = "INR"
)

data class OperatorCheckRequest(val mobileNumber: String)

data class OperatorCheckResponse(
    val mobileNumber: String,
    val operator: String,
    val providerOperator: String? = null,
    val circle: String,
    val type: String? = null,
    val providerOrderId: String? = null,
    val rechargeStatus: String = "UNKNOWN"
)

data class RechargePlan(
    val id: String,
    val amount: BigDecimal,
    val validity: String?,
    val description: String?
)

data class ErrorResponse(val message: String?)


data class RechargeHistoryItem(
    val transactionId: String,
    val clientRequestId: String,
    val mobileNumber: String,
    val operator: String,
    val circle: String,
    val planId: String,
    val planDescription: String? = null,
    val planValidity: String? = null,
    val amount: BigDecimal,
    val walletDebitAmount: BigDecimal,
    val status: String,
    val provider: String,
    val providerReference: String? = null,
    val providerOrderId: String? = null,
    val walletLedgerRef: String? = null,
    val completedAt: String? = null,
    val clientCommission: BigDecimal,
    val companyCommission: BigDecimal,
    val message: String? = null,
    val createdAt: String,
    val updatedAt: String
)

data class RechargeHistoryResponse(
    val items: List<RechargeHistoryItem>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class CommissionPeriodSummary(
    val from: String,
    val to: String,
    val commission: BigDecimal,
    val successfulRechargeAmount: BigDecimal,
    val successfulRechargeCount: Long
)

data class RechargeCommissionSummaryResponse(
    val commissionPercent: BigDecimal,
    val daily: CommissionPeriodSummary,
    val monthly: CommissionPeriodSummary
)


data class WalletHistoryItem(
    val id: Long,
    val type: String,
    val amount: BigDecimal,
    val status: String,
    val referenceType: String?,
    val referenceId: String?,
    val externalRef: String,
    val description: String?,
    val createdAt: String,
    val mobileNumber: String? = null,
    val operator: String? = null,
    val circle: String? = null
)

data class WalletHistoryResponse(
    val items: List<WalletHistoryItem>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val fromDate: String,
    val toDate: String
)

data class WithdrawMoneyRequest(val amount: BigDecimal, val upiId: String)
data class WithdrawMoneyResponse(
    val status: String,
    val amount: BigDecimal,
    val upiId: String,
    val balance: BigDecimal,
    val availableBalance: BigDecimal
)
