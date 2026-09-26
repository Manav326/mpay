package com.recharge.client.core.model

import java.math.BigDecimal

/** Mirrors the backend authentication and client DTOs. */
data class AccountDeletionRequest(
    val password: String,
    val confirmation: String
)

data class AccountDeletionResponse(
    val status: String,
    val message: String
)

data class LoginRequest(val mobile: String, val password: String)
data class RegisterRequest(
    val name: String?,
    val email: String?,
    val mobile: String,
    val password: String,
    val mobileVerificationToken: String? = null
)
data class OtpSendRequest(val mobile: String, val purpose: String)
data class OtpSendResponse(
    val status: String,
    val purpose: String,
    val expiresInSeconds: Long,
    val resendAfterSeconds: Long,
    val maskedMobile: String,
    val deliveryMode: String,
    val demoOtp: String? = null
)
data class OtpVerifyRequest(val mobile: String, val otp: String, val purpose: String)
data class OtpVerifyResponse(
    val verified: Boolean,
    val purpose: String,
    val verificationToken: String? = null,
    val verificationTokenExpiresInSeconds: Long? = null
)
data class ForgotPasswordRequest(val mobile: String)
data class ForgotPasswordResponse(
    val status: String,
    val expiresInSeconds: Long,
    val demoOtp: String? = null,
    val deliveryMode: String = "way2api",
    val resendAfterSeconds: Long = 60
)
data class ResetPasswordRequest(val mobile: String, val otp: String, val newPassword: String)
data class ResetPasswordResponse(val status: String)

data class RefreshTokenRequest(val refreshToken: String)
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val role: String,
    val mobileVerified: Boolean = false
)
data class CurrentUserResponse(
    val userId: Long,
    val publicUserId: String,
    val mobile: String,
    val name: String? = null,
    val email: String? = null,
    val profileImageUrl: String? = null,
    val profileImageVersion: Long? = null,
    val mobileVerified: Boolean = false,
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
    val providerCircle: String? = null,
    val circle: String,
    val type: String? = null,
    val providerOrderId: String? = null,
    val rechargeStatus: String = "UNKNOWN",
    val pending: Boolean = false,
    val message: String? = null,
    val providerMessageCode: String? = null
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
    val recipientName: String? = null,
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
    val provider: String? = null,
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

data class WithdrawMoneyRequest(
    val amount: BigDecimal,
    val provider: String = "razorpay",
    val clientRequestId: String,
    val upiId: String
)
data class WithdrawMoneyResponse(
    val withdrawalId: String,
    val status: String,
    val provider: String,
    val amount: BigDecimal,
    val upiId: String,
    val balance: BigDecimal,
    val availableBalance: BigDecimal,
    val message: String? = null
)

data class WithdrawalHistoryItem(
    val withdrawalId: String,
    val clientRequestId: String,
    val amount: BigDecimal,
    val upiId: String,
    val provider: String,
    val status: String,
    val providerReference: String? = null,
    val providerStatus: String? = null,
    val failureReason: String? = null,
    val walletLedgerRef: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String? = null
)

data class WithdrawalHistoryResponse(
    val items: List<WithdrawalHistoryItem>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)
