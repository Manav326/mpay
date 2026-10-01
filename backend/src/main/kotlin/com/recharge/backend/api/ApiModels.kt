package com.recharge.backend.api

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

data class AccountDeletionRequest(
    @field:NotBlank val password: String,
    @field:NotBlank val confirmation: String
)

data class AccountDeletionResponse(
    val status: String,
    val message: String
)

data class LoginRequest(
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Mobile number must be a valid 10 digit Indian mobile number")
    val mobile: String,
    @field:NotBlank
    val password: String
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val role: String,
    val permissions: Set<String> = emptySet(),
    val mobileVerified: Boolean = false
)

data class PortalLoginRequest(
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Mobile number must be a valid 10 digit Indian mobile number")
    val mobile: String,
    @field:NotBlank
    val password: String,
    @field:NotBlank
    val portalRole: String
)

data class PortalRolesResponse(val roles: List<String>)

data class OtpSendRequest(
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Mobile number must be a valid 10 digit Indian mobile number")
    val mobile: String,
    @field:NotBlank val purpose: String
)

data class OtpSendResponse(
    val status: String,
    val purpose: String,
    val expiresInSeconds: Long,
    val resendAfterSeconds: Long,
    val maskedMobile: String,
    val deliveryMode: String,
    val demoOtp: String? = null
)

data class OtpVerifyRequest(
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Mobile number must be a valid 10 digit Indian mobile number")
    val mobile: String,
    @field:Pattern(regexp = "[0-9]{6}", message = "OTP must be a 6 digit number")
    val otp: String,
    @field:NotBlank val purpose: String
)

data class OtpVerifyResponse(
    val verified: Boolean,
    val purpose: String,
    val verificationToken: String? = null,
    val verificationTokenExpiresInSeconds: Long? = null
)

data class RegisterRequest(
    @field:Size(max = 120, message = "Name must be 120 characters or fewer")
    val name: String?,
    @field:Email(message = "Email must be valid")
    @field:Size(max = 254, message = "Email must be 254 characters or fewer")
    val email: String?,
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Mobile number must be a valid 10 digit Indian mobile number")
    val mobile: String,
    @field:Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    val password: String,
    val mobileVerificationToken: String? = null
)

data class ForgotPasswordRequest(
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Mobile number must be a valid 10 digit Indian mobile number")
    val mobile: String
)

data class ForgotPasswordResponse(
    val status: String,
    val expiresInSeconds: Long,
    val demoOtp: String? = null,
    val deliveryMode: String = "way2api",
    val resendAfterSeconds: Long = 60
)

data class ResetPasswordRequest(
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Mobile number must be a valid 10 digit Indian mobile number")
    val mobile: String,
    @field:Pattern(regexp = "[0-9]{6}", message = "OTP must be a 6 digit number")
    val otp: String,
    @field:Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    val newPassword: String
)

data class RefreshTokenRequest(@field:NotBlank val refreshToken: String)

data class CurrentUserResponse(
    val userId: Long,
    val publicUserId: String,
    val mobile: String,
    val name: String?,
    val email: String?,
    val profileImageUrl: String?,
    val profileImageVersion: Long?,
    val mobileVerified: Boolean = false,
    val role: String,
    val commissionRate: BigDecimal,
    val createdAt: Instant,
    val profileUpdatedAt: Instant?
)

data class ProfileUpdateRequest(
    @field:Size(max = 120, message = "Name must be 120 characters or fewer")
    val name: String?,
    @field:Size(max = 254, message = "Email must be 254 characters or fewer")
    val email: String?
)

data class WalletResponse(
    val balance: BigDecimal,
    val availableBalance: BigDecimal,
    val reservedBalance: BigDecimal,
    val currency: String = "INR"
)

data class OperatorCheckRequest(@field:Pattern(regexp = "[6-9][0-9]{9}") val mobileNumber: String)
data class OperatorCheckResponse(
    val mobileNumber: String,
    val operator: String,
    val providerOperator: String,
    val providerCircle: String? = null,
    val circle: String,
    val type: String?,
    val providerOrderId: String?,
    val rechargeStatus: String = "UNKNOWN",
    val pending: Boolean = false,
    val message: String? = null,
    val providerMessageCode: String? = null
)
data class RechargePlanDto(val id: String, val amount: BigDecimal, val validity: String?, val description: String?)
data class CreatePaymentOrderRequest(
    @field:DecimalMin("0.01") val amount: BigDecimal,
    val provider: String = "razorpay",
    @field:NotBlank val clientRequestId: String,
    val purpose: String = "ADD_MONEY",
    val rechargeMobileNumber: String? = null,
    val rechargeOperator: String? = null,
    val rechargeCircle: String? = null,
    val rechargePlanId: String? = null,
    val rechargeRecipientName: String? = null
)

data class CreatePaymentOrderResponse(
    val provider: String = "razorpay",
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val keyId: String,
    val checkoutParams: Map<String, String> = emptyMap()
)

data class VerifyPaymentRequest(
    val provider: String = "razorpay",
    val paymentId: String? = null,
    val orderId: String? = null,
    val signature: String? = null
)

data class VerifyPaymentResponse(
    val status: String,
    val balance: BigDecimal,
    val availableBalance: BigDecimal = balance,
    val transactionId: String? = null,
    val rechargeStatus: String? = null,
    val amount: BigDecimal? = null,
    val commission: BigDecimal? = null,
    val walletDebitAmount: BigDecimal? = null,
    val message: String? = null,
    val currency: String = "INR"
)

data class PayUHashRequest(
    @field:NotBlank val hashName: String,
    @field:NotBlank val hashString: String,
    val postSalt: String? = null,
    val hashType: String? = null
)

data class PayUPaymentStatusRequest(
    @field:NotBlank val orderId: String,
    @field:NotBlank val status: String,
    val paymentId: String? = null,
    val signature: String? = null
)

data class PayUHashResponse(val hash: String)

data class RechargeResponse(
    val transactionId: String,
    val status: String,
    val amount: BigDecimal,
    val commission: BigDecimal,
    val walletDebitAmount: BigDecimal,
    val walletBalance: BigDecimal,
    val walletAvailableBalance: BigDecimal,
    val recipientName: String? = null
)

data class RechargeTransactionStatusResponse(
    val transactionId: String,
    val clientRequestId: String,
    val mobileNumber: String,
    val operator: String,
    val circle: String,
    val planId: String,
    val planDescription: String?,
    val planValidity: String?,
    val amount: BigDecimal,
    val walletDebitAmount: BigDecimal,
    val status: String,
    val provider: String,
    val providerReference: String?,
    val providerOrderId: String?,
    val walletLedgerRef: String?,
    val completedAt: Instant?,
    val clientCommission: BigDecimal,
    val companyCommission: BigDecimal,
    val message: String?,
    val walletBalance: BigDecimal,
    val walletAvailableBalance: BigDecimal,
    val recipientName: String? = null
)

data class RechargeRequest(
    @field:Pattern(regexp = "[6-9][0-9]{9}") val mobileNumber: String,
    @field:NotBlank val operator: String,
    @field:NotBlank val circle: String,
    @field:NotBlank val planId: String,
    @field:NotBlank @field:Size(max = 100) val clientRequestId: String,
    @field:Size(max = 120) val recipientName: String? = null
)

data class RechargeHistoryItem(
    val transactionId: String,
    val clientRequestId: String,
    val mobileNumber: String,
    val operator: String,
    val circle: String,
    val planId: String,
    val planDescription: String?,
    val planValidity: String?,
    val amount: BigDecimal,
    val walletDebitAmount: BigDecimal,
    val status: String,
    val provider: String,
    val providerReference: String?,
    val providerOrderId: String?,
    val walletLedgerRef: String?,
    val completedAt: Instant?,
    val clientCommission: BigDecimal,
    val companyCommission: BigDecimal,
    val message: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val recipientName: String? = null
)

data class RechargeHistoryResponse(
    val items: List<RechargeHistoryItem>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val fromDate: String,
    val toDate: String
)

data class CommissionPeriodSummary(
    val from: Instant,
    val to: Instant,
    val commission: BigDecimal,
    val successfulRechargeAmount: BigDecimal,
    val successfulRechargeCount: Long,
    val upstreamCommission: BigDecimal = BigDecimal.ZERO
)

data class RechargeCommissionSummaryResponse(
    val commissionPercent: BigDecimal,
    val daily: CommissionPeriodSummary,
    val monthly: CommissionPeriodSummary,
    val upstreamCommissionPercent: BigDecimal = BigDecimal.ZERO,
    val level: Int = 0,
    val directClientCount: Int = 0,
    val level2DirectClientThreshold: Int = 5,
    val upstreamEligible: Boolean = false
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
    val createdAt: Instant,
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
    @field:DecimalMin("1.00") val amount: BigDecimal,
    val provider: String = "mock",
    @field:NotBlank @field:Size(max = 100) val clientRequestId: String,
    @field:NotBlank
    @field:Size(max = 254)
    @field:Pattern(regexp = "^[A-Za-z0-9]+@[A-Za-z]+$", message = "Enter a valid UPI ID")
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
    val providerReference: String?,
    val providerStatus: String?,
    val failureReason: String?,
    val walletLedgerRef: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?
)

data class WithdrawalHistoryResponse(
    val items: List<WithdrawalHistoryItem>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class RoleCommissionRateResponse(
    val role: String,
    val commissionPercent: BigDecimal,
    val active: Boolean
)

data class UpdateRoleCommissionRateRequest(
    @field:DecimalMin("0.00") val commissionPercent: BigDecimal,
    val active: Boolean = true
)

data class CreatePortalStaffRequest(
    @field:jakarta.validation.constraints.NotBlank
    @field:jakarta.validation.constraints.Size(max = 120, message = "Name must be 120 characters or fewer")
    val name: String,
    @field:jakarta.validation.constraints.Email(message = "Email must be valid")
    @field:jakarta.validation.constraints.Size(max = 254, message = "Email must be 254 characters or fewer")
    val email: String? = null,
    @field:jakarta.validation.constraints.Pattern(
        regexp = "[6-9][0-9]{9}",
        message = "Mobile number must be a valid 10 digit Indian mobile number"
    )
    val mobile: String,
    @field:jakarta.validation.constraints.Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    val password: String,
    @field:jakarta.validation.constraints.NotBlank
    val role: String
)

data class PortalStaffResponse(
    val publicUserId: String,
    val name: String?,
    val email: String?,
    val mobile: String,
    val role: String,
    val active: Boolean,
    val createdAt: Instant,
    val lastLoginAt: Instant? = null
)

data class PortalStaffActivityResponse(
    val action: String,
    val subjectType: String?,
    val subjectId: String?,
    val summary: String,
    val occurredAt: Instant
)

data class PortalStaffStatusResponse(
    val publicUserId: String,
    val active: Boolean,
    val status: String
)

data class AdminUserStatusRequest(
    val active: Boolean
)

data class AdminUserStatusResponse(
    val publicUserId: String,
    val active: Boolean,
    val status: String
)

data class AdminUserMobileVerificationRequest(
    val verified: Boolean,
    @field:jakarta.validation.constraints.Size(max = 1000)
    val reason: String? = null
)

data class AdminUserMobileVerificationResponse(
    val publicUserId: String,
    val mobileVerified: Boolean,
    val mobileVerifiedAt: Instant?
)

data class AdminFinancialRechargeOperation(
    val transactionId: String,
    val userPublicId: String,
    val userName: String,
    val userMobile: String,
    val mobileNumber: String,
    val operator: String,
    val circle: String,
    val amount: BigDecimal,
    val walletDebitAmount: BigDecimal,
    val clientCommission: BigDecimal,
    val companyCommission: BigDecimal,
    val status: String,
    val provider: String,
    val providerReference: String?,
    val providerOrderId: String?,
    val walletLedgerRef: String?,
    val message: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class AdminFinancialRechargePageResponse(
    val items: List<AdminFinancialRechargeOperation>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class AdminFinancialWithdrawalOperation(
    val withdrawalId: String,
    val userPublicId: String,
    val userName: String,
    val userMobile: String,
    val amount: BigDecimal,
    val upiId: String,
    val provider: String,
    val status: String,
    val providerReference: String?,
    val providerStatus: String?,
    val failureReason: String?,
    val walletLedgerRef: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?
)

data class AdminFinancialWithdrawalPageResponse(
    val items: List<AdminFinancialWithdrawalOperation>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class AdminFinancialWalletOperation(
    val id: Long,
    val userPublicId: String,
    val userName: String,
    val userMobile: String,
    val type: String,
    val amount: BigDecimal,
    val status: String,
    val referenceType: String?,
    val referenceId: String?,
    val externalRef: String,
    val description: String?,
    val createdAt: Instant
)

data class AdminFinancialWalletPageResponse(
    val items: List<AdminFinancialWalletOperation>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class AdminUserSummaryResponse(
    val id: String,
    val publicUserId: String,
    val name: String,
    val mobile: String,
    val email: String,
    val role: String,
    val accountType: String,
    val todayEarnings: BigDecimal,
    val monthEarnings: BigDecimal,
    val todayVolume: BigDecimal,
    val monthVolume: BigDecimal,
    val walletBalance: BigDecimal,
    val joinedAt: Instant,
    val profileUpdatedAt: Instant?,
    val status: String,
    val mobileVerified: Boolean,
    val mobileVerifiedAt: Instant?
)

data class AdminLatestRechargeResponse(
    val mobile: String,
    val operator: String,
    val amount: BigDecimal,
    val commission: BigDecimal,
    val status: String,
    val createdAt: Instant,
    val transactionId: String
)

data class AdminWalletEntryResponse(
    val id: String,
    val type: String,
    val amount: BigDecimal,
    val createdAt: Instant,
    val reference: String
)

data class AdminUserDetailResponse(
    val summary: AdminUserSummaryResponse,
    val rechargeCount: Long,
    val addMoneyTotal: BigDecimal,
    val withdrawalTotal: BigDecimal,
    val commissionRate: BigDecimal,
    val balance: BigDecimal,
    val availableBalance: BigDecimal,
    val reservedBalance: BigDecimal,
    val profileImageUrl: String?,
    val profileImageVersion: Long?,
    val latestRecharge: AdminLatestRechargeResponse?,
    val recentWalletEntries: List<AdminWalletEntryResponse>
)

data class AdminDashboardChartPoint(
    val label: String,
    val volume: BigDecimal,
    val commission: BigDecimal
)

data class AdminDashboardResponse(
    val todayVolume: BigDecimal,
    val todayCommission: BigDecimal,
    val monthlyVolume: BigDecimal,
    val monthlyCommission: BigDecimal,
    val activeClients: Int,
    val successfulRecharges: Long,
    val totalUsers: Long,
    val chart: List<AdminDashboardChartPoint>,
    val from: Instant,
    val to: Instant,
    val monthFrom: Instant,
    val monthTo: Instant
)



data class HistoryPdfAccessRequest(
    @field:jakarta.validation.constraints.Size(min = 20, max = 1000)
    val reason: String
)

data class HistoryPdfAccessDecisionRequest(
    @field:jakarta.validation.constraints.NotBlank
    val action: String,
    @field:jakarta.validation.constraints.Size(max = 1000)
    val reviewNote: String? = null,
    @field:jakarta.validation.constraints.NotNull
    val requestId: Long
)

data class HistoryPdfAccessResponse(
    val status: String,
    val requestId: Long?,
    val requestReason: String?,
    val reviewNote: String?,
    val requestedAt: Instant?,
    val reviewedAt: Instant?
)

data class HistoryPdfPendingAccessResponse(
    val requestId: Long,
    val publicUserId: String,
    val customerName: String?,
    val mobile: String,
    val requestReason: String,
    val requestedAt: Instant
)


data class CallIceServerResponse(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

data class VoiceCallResponse(
    val callId: String,
    val status: String,
    val callerName: String?,
    val callerPublicId: String,
    val calleeName: String?,
    val calleePublicId: String,
    val createdAt: String,
    val ringingExpiresAt: String,
    val acceptedAt: String? = null,
    val connectedAt: String? = null,
    val endedAt: String? = null,
    val endedReason: String? = null,
    val iceServers: List<CallIceServerResponse> = emptyList()
)

data class CreateVoiceCallRequest(
    @field:jakarta.validation.constraints.NotBlank
    val targetPublicId: String,
    @field:jakarta.validation.constraints.Size(max = 40)
    val supportRequestId: String? = null
)

data class VoiceCallSignalingTokenRequest(
    @field:jakarta.validation.constraints.NotBlank
    val callId: String
)

data class VoiceCallSignalingTokenResponse(
    val token: String,
    val expiresInSeconds: Long,
    val websocketPath: String
)

data class CallPushTokenRequest(
    @field:jakarta.validation.constraints.NotBlank
    val token: String,
    val platform: String = "ANDROID"
)

data class VoiceCallRoleAccessResponse(
    val role: String,
    val enabled: Boolean
)

data class VoiceCallRoleAccessRequest(
    val enabled: Boolean
)

data class VoiceCallUserAccessResponse(
    val publicUserId: String,
    val name: String?,
    val mobile: String,
    val role: String,
    val mode: String,
    val enabled: Boolean
)

data class VoiceCallUserAccessRequest(
    @field:jakarta.validation.constraints.NotBlank
    val mode: String
)

data class VoiceCallStatusBroadcast(
    val type: String = "status",
    val callId: String,
    val status: String
)


data class ClientCommissionOverviewResponse(
    val publicUserId: String,
    val level: Int,
    val baseCommissionPercent: BigDecimal,
    val directClientCount: Int,
    val level2DirectClientThreshold: Int,
    val level2Qualified: Boolean,
    val canAddClients: Boolean,
    val upstreamCommissionPercent: BigDecimal,
    val upstreamCommissionActive: Boolean,
    val upstreamEligible: Boolean,
    val parent: ClientReferralMemberResponse?,
    val todayUpstreamCommission: BigDecimal,
    val monthUpstreamCommission: BigDecimal
)

data class ClientReferralMemberResponse(
    val publicUserId: String,
    val name: String?,
    val mobile: String,
    val assignedAt: Instant
)

data class ClientSearchResultResponse(
    val publicUserId: String,
    val name: String?,
    val mobile: String,
    val email: String?,
    val profileImageUrl: String?,
    val profileImageVersion: Long?,
    val createdAt: Instant,
    val accountActive: Boolean,
    val mobileVerified: Boolean,
    val clientLevel: Int,
    val directClientCount: Int,
    val alreadyAssigned: Boolean,
    val canBeAdded: Boolean,
    val unavailableReason: String?
)

data class AddClientCommissionMemberRequest(
    @field:NotBlank @field:Size(max = 36) val clientPublicId: String
)

data class ClientUpstreamCommissionHistoryItem(
    val childPublicUserId: String,
    val childName: String?,
    val childMobile: String,
    val rechargeTransactionId: String,
    val rechargeAmount: BigDecimal,
    val commissionPercent: BigDecimal,
    val commissionAmount: BigDecimal,
    val walletLedgerRef: String,
    val createdAt: Instant
)

data class ClientUpstreamCommissionPageResponse(
    val items: List<ClientUpstreamCommissionHistoryItem>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class ClientCommissionSettingsResponse(
    val level2DirectClientThreshold: Int,
    val upstreamCommissionPercent: BigDecimal,
    val upstreamCommissionActive: Boolean
)

data class UpdateClientCommissionSettingsRequest(
    @field:jakarta.validation.constraints.Min(1) val level2DirectClientThreshold: Int,
    @field:DecimalMin("0.00") val upstreamCommissionPercent: BigDecimal,
    val upstreamCommissionActive: Boolean = true
)
