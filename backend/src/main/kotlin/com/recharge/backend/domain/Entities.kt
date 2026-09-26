package com.recharge.backend.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity @Table(name = "users")
class UserEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "public_id", nullable = false, unique = true, length = 36) var publicId: String = java.util.UUID.randomUUID().toString(),
    @Column(nullable = false, unique = true) var mobile: String = "",
    @Column(nullable = true, length = 120) var name: String? = null,
    @Column(nullable = true, length = 254) var email: String? = null,
    @Column(name = "profile_image_key", length = 255) var profileImageKey: String? = null,
    @Column(name = "profile_image_content_type", length = 100) var profileImageContentType: String? = null,
    @Column(name = "profile_image_updated_at") var profileImageUpdatedAt: Instant? = null,
    @Column(name = "profile_updated_at") var profileUpdatedAt: Instant? = Instant.now(),
    @Column(nullable = false) var passwordHash: String = "",
    @Column(nullable = false) var role: String = "CLIENT",
    @Column(nullable = false) var active: Boolean = true,
    @Column(name = "mobile_verified_at") var mobileVerifiedAt: Instant? = null,
    @Column(name = "deleted_at") var deletedAt: Instant? = null,
    @Column(nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "wallets")
class WalletEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @OneToOne @JoinColumn(name = "user_id", nullable = false, unique = true) var user: UserEntity? = null,
    @Column(nullable = false, precision = 19, scale = 2) var balance: BigDecimal = BigDecimal.ZERO,
    @Column(name = "reserved_balance", nullable = false, precision = 19, scale = 2) var reservedBalance: BigDecimal = BigDecimal.ZERO,
    @Version var version: Long? = null
)

@Entity @Table(name = "wallet_transactions")
class WalletTransactionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false) var externalRef: String = "",
    @Column(nullable = false) var userId: Long = 0,
    @Column(nullable = false) var type: String = "DEBIT",
    @Column(nullable = false, precision = 19, scale = 2) var amount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false) var status: String = "POSTED",
    @Column(name = "reference_type", length = 40) var referenceType: String? = null,
    @Column(name = "reference_id", length = 150) var referenceId: String? = null,
    @Column(length = 300) var description: String? = null,
    @Column(nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "recharge_transactions")
class RechargeTransactionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, unique = true) var transactionId: String = "",
    @Column(name = "client_request_id", nullable = false, length = 100) var clientRequestId: String = "",
    @Column(nullable = false) var userId: Long = 0,
    @Column(nullable = false) var mobileNumber: String = "",
    @Column(name = "recipient_name", length = 120) var recipientName: String? = null,
    @Column(nullable = false, length = 30) var operator: String = "",
    @Column(nullable = false, length = 100) var circle: String = "",
    @Column(nullable = false) var planId: String = "",
    @Column(name = "plan_description", length = 1000) var planDescription: String? = null,
    @Column(name = "plan_validity", length = 100) var planValidity: String? = null,
    @Column(nullable = false, precision = 19, scale = 2) var amount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "wallet_debit_amount", nullable = false, precision = 19, scale = 2) var walletDebitAmount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 19, scale = 4) var companyCommission: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 19, scale = 4) var clientCommission: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, length = 30) var status: String = "RESERVED",
    @Column(name = "provider_name", nullable = false, length = 50) var providerName: String = "MOCK",
    @Column(name = "provider_reference", length = 150) var providerReference: String? = null,
    @Column(name = "provider_order_id", length = 150) var providerOrderId: String? = null,
    @Column(name = "wallet_ledger_ref", length = 150) var walletLedgerRef: String? = null,
    @Column(name = "completed_at") var completedAt: Instant? = null,
    @Column(length = 500) var message: String? = null,
    @Column(nullable = false) var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "recharge_offer_cache",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_offer_cache_key_offer", columnNames = ["cache_key", "offer_id"])
    ],
    indexes = [
        Index(name = "idx_offer_cache_key_expiry", columnList = "cache_key,expires_at"),
        Index(name = "idx_offer_cache_offer_expiry", columnList = "offer_id,expires_at")
    ]
)
class RechargeOfferCacheEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "cache_key", nullable = false, length = 220) var cacheKey: String = "",
    @Column(name = "offer_id", nullable = false, length = 150) var offerId: String = "",
    @Column(name = "mobile_number", nullable = false, length = 15) var mobileNumber: String = "",
    @Column(nullable = false, length = 30) var operator: String = "",
    @Column(nullable = false, length = 100) var circle: String = "",
    @Column(nullable = false, precision = 19, scale = 2) var amount: BigDecimal = BigDecimal.ZERO,
    @Column(length = 100) var validity: String? = null,
    @Column(length = 1000) var description: String? = null,
    @Column(name = "provider_reference", length = 150) var providerReference: String? = null,
    @Column(name = "provider_order_id", length = 150) var providerOrderId: String? = null,
    @Column(name = "provider_log_description", length = 1500) var providerLogDescription: String? = null,
    @Column(name = "provider_metadata", length = 3000) var providerMetadata: String? = null,
    @Column(name = "fetched_at", nullable = false) var fetchedAt: Instant = Instant.now(),
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant = Instant.now()
)


@Entity
@Table(name = "role_commission_rates")
class RoleCommissionRateEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, unique = true, length = 30) var role: String = "CLIENT",
    @Column(name = "commission_percent", nullable = false, precision = 7, scale = 4) var commissionPercent: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false) var active: Boolean = true,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)


@Entity
@Table(name = "password_reset_otps")
class PasswordResetOtpEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, length = 10) var mobile: String = "",
    @Column(nullable = false, length = 32) var purpose: String = "PASSWORD_RESET",
    @Column(name = "otp_hash", nullable = true, length = 100) var otpHash: String? = null,
    @Column(name = "verification_sid", length = 34) var verificationSid: String? = null,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant = Instant.now(),
    @Column(nullable = false) var attempts: Int = 0,
    @Column(name = "used_at") var usedAt: Instant? = null,
    @Column(name = "last_sent_at") var lastSentAt: Instant? = null,
    @Column(name = "send_window_started_at") var sendWindowStartedAt: Instant? = null,
    @Column(name = "send_count", nullable = false) var sendCount: Int = 0,
    @Column(name = "provider_order_id", length = 150) var providerOrderId: String? = null,
    @Column(name = "verification_token_hash", length = 64) var verificationTokenHash: String? = null,
    @Column(name = "verification_token_expires_at") var verificationTokenExpiresAt: Instant? = null,
    @Column(name = "verified_at") var verifiedAt: Instant? = null,
    @Column(nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)


@Entity
@Table(
    name = "role_permissions",
    uniqueConstraints = [UniqueConstraint(name = "uq_role_permissions_role_permission", columnNames = ["role", "permission"])]
)
class RolePermissionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, length = 50) var role: String = "CLIENT",
    @Column(nullable = false, length = 80) var permission: String = "PORTAL_LOGIN"
)

@Entity
@Table(
    name = "role_hierarchy",
    uniqueConstraints = [UniqueConstraint(name = "uq_role_hierarchy_viewer_target", columnNames = ["viewer_role", "target_role"])]
)
class RoleHierarchyEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "viewer_role", nullable = false, length = 50) var viewerRole: String = "ADMIN",
    @Column(name = "target_role", nullable = false, length = 50) var targetRole: String = "CLIENT"
)


@Entity
@Table(
    name = "wallet_withdrawals",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_wallet_withdrawal_user_request", columnNames = ["user_id", "client_request_id"])
    ],
    indexes = [
        Index(name = "idx_wallet_withdrawal_user_created", columnList = "user_id, created_at"),
        Index(name = "idx_wallet_withdrawal_provider_ref", columnList = "provider_name, provider_reference")
    ]
)
class WalletWithdrawalEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "withdrawal_id", nullable = false, unique = true, length = 40) var withdrawalId: String = "",
    @Column(name = "client_request_id", nullable = false, length = 100) var clientRequestId: String = "",
    @Column(nullable = false) var userId: Long = 0,
    @Column(nullable = false, precision = 19, scale = 2) var amount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "upi_id", nullable = false, length = 254) var upiId: String = "",
    @Column(name = "provider_name", nullable = false, length = 30) var providerName: String = "",
    @Column(nullable = false, length = 30) var status: String = "PENDING",
    @Column(name = "provider_reference", length = 150) var providerReference: String? = null,
    @Column(name = "provider_status", length = 50) var providerStatus: String? = null,
    @Column(name = "failure_reason", length = 500) var failureReason: String? = null,
    @Column(name = "wallet_ledger_ref", length = 150) var walletLedgerRef: String? = null,
    @Column(nullable = false) var createdAt: Instant = Instant.now(),
    @Column(nullable = false) var updatedAt: Instant = Instant.now(),
    @Column(name = "completed_at") var completedAt: Instant? = null
)
