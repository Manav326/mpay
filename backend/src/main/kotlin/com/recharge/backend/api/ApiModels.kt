package com.recharge.backend.api

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

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
    val permissions: Set<String> = emptySet()
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

data class RegisterRequest(
    @field:Size(max = 120, message = "Name must be 120 characters or fewer")
    val name: String?,
    @field:Email(message = "Email must be valid")
    @field:Size(max = 254, message = "Email must be 254 characters or fewer")
    val email: String?,
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Mobile number must be a valid 10 digit Indian mobile number")
    val mobile: String,
    @field:Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    val password: String
)

data class ForgotPasswordRequest(
    @field:Pattern(regexp = "[6-9][0-9]{9}", message = "Mobile number must be a valid 10 digit Indian mobile number")
    val mobile: String
)

data class ForgotPasswordResponse(
    val status: String,
    val expiresInSeconds: Long,
    val demoOtp: String? = null,
    val deliveryMode: String = "twilio"
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
    @field:NotBlank val clientRequestId: String,
    val purpose: String = "ADD_MONEY",
    val rechargeMobileNumber: String? = null,
    val rechargeOperator: String? = null,
    val rechargeCircle: String? = null,
    val rechargePlanId: String? = null
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
    val transactionId: String? = null,
    val rechargeStatus: String? = null,
    val message: String? = null,
    val currency: String = "INR"
)

data class PayUHashRequest(
    @field:NotBlank val hashName: String,
    @field:NotBlank val hashString: String,
    val postSalt: String? = null,
    val hashType: String? = null
)

data class PayUHashResponse(val hash: String)

data class RechargeResponse(
    val transactionId: String,
    val status: String,
    val amount: BigDecimal,
    val commission: BigDecimal,
    val walletDebitAmount: BigDecimal,
    val walletBalance: BigDecimal,
    val walletAvailableBalance: BigDecimal
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
    val walletAvailableBalance: BigDecimal
)

data class RechargeRequest(
    @field:Pattern(regexp = "[6-9][0-9]{9}") val mobileNumber: String,
    @field:NotBlank val operator: String,
    @field:NotBlank val circle: String,
    @field:NotBlank val planId: String,
    @field:NotBlank @field:Size(max = 100) val clientRequestId: String
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
    val updatedAt: Instant
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
    val createdAt: Instant,
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
    @field:NotBlank val upiId: String
)

data class WithdrawMoneyResponse(
    val status: String,
    val amount: BigDecimal,
    val upiId: String,
    val balance: BigDecimal,
    val availableBalance: BigDecimal
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
    val status: String
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

data class AdminVendorResponse(
    val id: String,
    val name: String,
    val category: String,
    val city: String,
    val phone: String,
    val commissionRate: BigDecimal,
    val active: Boolean,
    val createdAt: Instant
)

data class CreateAdminVendorRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val category: String,
    @field:NotBlank val city: String,
    @field:NotBlank val phone: String,
    @field:DecimalMin("0.00") val commissionRate: BigDecimal,
    val active: Boolean = true
)
