package com.recharge.backend.repository

import com.recharge.backend.domain.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByMobile(mobile: String): Optional<UserEntity>
    fun findByEmailIgnoreCase(email: String): Optional<UserEntity>
    fun existsByEmailIgnoreCaseAndIdNot(email: String, id: Long): Boolean
    fun findByPublicId(publicId: String): Optional<UserEntity>
    fun findAllByRoleIn(roles: Collection<String>): List<UserEntity>
    fun findAllByRoleInOrderByCreatedAtDesc(roles: Collection<String>): List<UserEntity>
}

interface WalletRepository : JpaRepository<WalletEntity, Long> {
    fun findByUserId(userId: Long): Optional<WalletEntity>

    @Query("select w from WalletEntity w where w.user.id in :userIds")
    fun findAllByUserIds(@Param("userIds") userIds: Collection<Long>): List<WalletEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletEntity w where w.user.id = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): Optional<WalletEntity>
}

interface WalletWithdrawalRepository : JpaRepository<WalletWithdrawalEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUserIdAndClientRequestId(userId: Long, clientRequestId: String): Optional<WalletWithdrawalEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByWithdrawalId(withdrawalId: String): Optional<WalletWithdrawalEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByWithdrawalIdAndProviderName(withdrawalId: String, providerName: String): Optional<WalletWithdrawalEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByProviderNameAndProviderReference(providerName: String, providerReference: String): Optional<WalletWithdrawalEntity>

    fun findTop20ByUserIdOrderByCreatedAtDesc(userId: Long): List<WalletWithdrawalEntity>

    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<WalletWithdrawalEntity>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<WalletWithdrawalEntity>
    fun findAllByUserIdInOrderByCreatedAtDesc(userIds: Collection<Long>, pageable: Pageable): Page<WalletWithdrawalEntity>
    fun findAllByUserIdInAndStatusOrderByCreatedAtDesc(userIds: Collection<Long>, status: String, pageable: Pageable): Page<WalletWithdrawalEntity>
    fun findAllByUserIdInAndProviderNameOrderByCreatedAtDesc(userIds: Collection<Long>, providerName: String, pageable: Pageable): Page<WalletWithdrawalEntity>
    fun findAllByUserIdInAndStatusAndProviderNameOrderByCreatedAtDesc(userIds: Collection<Long>, status: String, providerName: String, pageable: Pageable): Page<WalletWithdrawalEntity>
    fun findAllByStatusOrderByCreatedAtDesc(status: String, pageable: Pageable): Page<WalletWithdrawalEntity>
    fun findAllByProviderNameOrderByCreatedAtDesc(providerName: String, pageable: Pageable): Page<WalletWithdrawalEntity>
    fun findAllByStatusAndProviderNameOrderByCreatedAtDesc(status: String, providerName: String, pageable: Pageable): Page<WalletWithdrawalEntity>
}

interface WalletTransactionRepository : JpaRepository<WalletTransactionEntity, Long> {
    fun existsByExternalRef(externalRef: String): Boolean
    fun findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId: Long, fromInclusive: Instant, toExclusive: Instant, pageable: Pageable): Page<WalletTransactionEntity>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<WalletTransactionEntity>
    fun findAllByReferenceTypeOrderByCreatedAtDesc(referenceType: String, pageable: Pageable): Page<WalletTransactionEntity>
    fun findAllByUserIdInOrderByCreatedAtDesc(userIds: Collection<Long>, pageable: Pageable): Page<WalletTransactionEntity>
    fun findAllByUserIdInAndReferenceTypeOrderByCreatedAtDesc(userIds: Collection<Long>, referenceType: String, pageable: Pageable): Page<WalletTransactionEntity>
    fun findByUserIdAndReferenceTypeAndCreatedAtBetweenOrderByCreatedAtDesc(userId: Long, referenceType: String, fromInclusive: Instant, toExclusive: Instant, pageable: Pageable): Page<WalletTransactionEntity>
    fun findByUserIdAndReferenceTypeInAndCreatedAtBetweenOrderByCreatedAtDesc(userId: Long, referenceTypes: Collection<String>, fromInclusive: Instant, toExclusive: Instant, pageable: Pageable): Page<WalletTransactionEntity>

    @Query("""
        select coalesce(sum(w.amount), 0)
        from WalletTransactionEntity w
        where w.userId = :userId and w.type = 'CREDIT' and w.referenceType = 'ADD_MONEY'
          and w.createdAt >= :fromInclusive and w.createdAt < :toExclusive
    """)
    fun sumAddMoney(userId: Long, fromInclusive: Instant, toExclusive: Instant): BigDecimal

    @Query("""
        select coalesce(sum(w.amount), 0) from WalletTransactionEntity w
        where w.userId = :userId and w.type = 'CREDIT' and w.referenceType = 'ADD_MONEY'
    """)
    fun sumAddMoneyAllTime(@Param("userId") userId: Long): BigDecimal

    @Query("""
        select coalesce(sum(w.amount), 0) from WalletTransactionEntity w
        where w.userId = :userId and w.referenceType = 'WITHDRAWAL' and w.status = 'POSTED'
    """)
    fun sumWithdrawalsAllTime(@Param("userId") userId: Long): BigDecimal

    fun findTop10ByUserIdOrderByCreatedAtDesc(userId: Long): List<WalletTransactionEntity>
}

interface RolePermissionRepository : JpaRepository<RolePermissionEntity, Long> {
    fun findAllByRoleIgnoreCaseOrderByPermissionAsc(role: String): List<RolePermissionEntity>
    fun findAllByPermissionIgnoreCaseOrderByRoleAsc(permission: String): List<RolePermissionEntity>
    fun existsByRoleIgnoreCaseAndPermissionIgnoreCase(role: String, permission: String): Boolean
}

interface RoleHierarchyRepository : JpaRepository<RoleHierarchyEntity, Long> {
    fun findAllByViewerRoleIgnoreCaseOrderByTargetRoleAsc(viewerRole: String): List<RoleHierarchyEntity>
    fun existsByViewerRoleIgnoreCaseAndTargetRoleIgnoreCase(viewerRole: String, targetRole: String): Boolean
}

interface RoleCommissionRateRepository : JpaRepository<RoleCommissionRateEntity, Long> {
    fun findByRoleIgnoreCaseAndActiveTrue(role: String): Optional<RoleCommissionRateEntity>
    fun findAllByOrderByRoleAsc(): List<RoleCommissionRateEntity>
    fun findByRoleIgnoreCase(role: String): Optional<RoleCommissionRateEntity>
}

interface UserRechargeSummaryProjection {
    val userId: Long
    val clientCommission: BigDecimal
    val amount: BigDecimal
    val successfulCount: Long
}

interface RechargeTransactionRepository : JpaRepository<RechargeTransactionEntity, Long> {
    fun findByTransactionId(transactionId: String): Optional<RechargeTransactionEntity>
    fun findAllByTransactionIdIn(transactionIds: Collection<String>): List<RechargeTransactionEntity>
    fun findByClientRequestIdAndUserId(clientRequestId: String, userId: Long): Optional<RechargeTransactionEntity>
    fun findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId: Long, fromInclusive: Instant, toInclusive: Instant, pageable: Pageable): Page<RechargeTransactionEntity>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<RechargeTransactionEntity>
    fun findAllByStatusOrderByCreatedAtDesc(status: String, pageable: Pageable): Page<RechargeTransactionEntity>
    fun findAllByProviderNameOrderByCreatedAtDesc(providerName: String, pageable: Pageable): Page<RechargeTransactionEntity>
    fun findAllByUserIdInOrderByCreatedAtDesc(userIds: Collection<Long>, pageable: Pageable): Page<RechargeTransactionEntity>
    fun findAllByUserIdInAndStatusOrderByCreatedAtDesc(userIds: Collection<Long>, status: String, pageable: Pageable): Page<RechargeTransactionEntity>
    fun findAllByUserIdInAndProviderNameOrderByCreatedAtDesc(userIds: Collection<Long>, providerName: String, pageable: Pageable): Page<RechargeTransactionEntity>
    fun findAllByUserIdInAndStatusAndProviderNameOrderByCreatedAtDesc(userIds: Collection<Long>, status: String, providerName: String, pageable: Pageable): Page<RechargeTransactionEntity>
    fun findAllByStatusAndProviderNameOrderByCreatedAtDesc(status: String, providerName: String, pageable: Pageable): Page<RechargeTransactionEntity>

    @Query("""
        select coalesce(sum(r.clientCommission), 0)
        from RechargeTransactionEntity r
        where r.userId = :userId
          and r.status = 'SUCCESS'
          and coalesce(r.completedAt, r.createdAt) >= :fromInclusive
          and coalesce(r.completedAt, r.createdAt) < :toExclusive
    """)
    fun sumClientCommission(
        @Param("userId") userId: Long,
        @Param("fromInclusive") fromInclusive: Instant,
        @Param("toExclusive") toExclusive: Instant
    ): BigDecimal

    @Query("""
        select coalesce(sum(r.amount), 0)
        from RechargeTransactionEntity r
        where r.userId = :userId
          and r.status = 'SUCCESS'
          and coalesce(r.completedAt, r.createdAt) >= :fromInclusive
          and coalesce(r.completedAt, r.createdAt) < :toExclusive
    """)
    fun sumSuccessfulRechargeAmount(
        @Param("userId") userId: Long,
        @Param("fromInclusive") fromInclusive: Instant,
        @Param("toExclusive") toExclusive: Instant
    ): BigDecimal

    @Query("""
        select count(r)
        from RechargeTransactionEntity r
        where r.userId = :userId
          and r.status = 'SUCCESS'
          and coalesce(r.completedAt, r.createdAt) >= :fromInclusive
          and coalesce(r.completedAt, r.createdAt) < :toExclusive
    """)
    fun countSuccessfulRecharges(
        @Param("userId") userId: Long,
        @Param("fromInclusive") fromInclusive: Instant,
        @Param("toExclusive") toExclusive: Instant
    ): Long

    fun countSuccessfulByUserId(userId: Long): Long = countSuccessfulRecharges(userId, Instant.EPOCH, Instant.ofEpochMilli(Long.MAX_VALUE))

    @Query("""
        select r.userId as userId,
               coalesce(sum(r.clientCommission), 0) as clientCommission,
               coalesce(sum(r.amount), 0) as amount,
               count(r) as successfulCount
        from RechargeTransactionEntity r
        where r.userId in :userIds
          and r.status = 'SUCCESS'
          and coalesce(r.completedAt, r.createdAt) >= :fromInclusive
          and coalesce(r.completedAt, r.createdAt) < :toExclusive
        group by r.userId
    """)
    fun aggregateSuccessfulForUsers(
        @Param("userIds") userIds: Collection<Long>,
        @Param("fromInclusive") fromInclusive: Instant,
        @Param("toExclusive") toExclusive: Instant
    ): List<UserRechargeSummaryProjection>

    fun findTopByUserIdOrderByCreatedAtDesc(userId: Long): RechargeTransactionEntity?

    @Query("""
        select coalesce(sum(r.amount), 0) from RechargeTransactionEntity r
        where r.userId in :userIds and r.status = 'SUCCESS'
          and coalesce(r.completedAt, r.createdAt) >= :fromInclusive
          and coalesce(r.completedAt, r.createdAt) < :toExclusive
    """)
    fun sumAmountForUsers(@Param("userIds") userIds: Collection<Long>, @Param("fromInclusive") fromInclusive: Instant, @Param("toExclusive") toExclusive: Instant): BigDecimal

    @Query("""
        select coalesce(sum(r.companyCommission), 0) from RechargeTransactionEntity r
        where r.userId in :userIds and r.status = 'SUCCESS'
          and coalesce(r.completedAt, r.createdAt) >= :fromInclusive
          and coalesce(r.completedAt, r.createdAt) < :toExclusive
    """)
    fun sumCompanyCommissionForUsers(@Param("userIds") userIds: Collection<Long>, @Param("fromInclusive") fromInclusive: Instant, @Param("toExclusive") toExclusive: Instant): BigDecimal

    @Query("""
        select count(r) from RechargeTransactionEntity r
        where r.userId in :userIds and r.status = 'SUCCESS'
          and coalesce(r.completedAt, r.createdAt) >= :fromInclusive
          and coalesce(r.completedAt, r.createdAt) < :toExclusive
    """)
    fun countSuccessfulForUsers(@Param("userIds") userIds: Collection<Long>, @Param("fromInclusive") fromInclusive: Instant, @Param("toExclusive") toExclusive: Instant): Long
}

interface RechargeOfferCacheRepository : JpaRepository<RechargeOfferCacheEntity, Long> {
    fun findByCacheKeyAndExpiresAtAfterOrderByAmountAsc(cacheKey: String, now: Instant): List<RechargeOfferCacheEntity>
    fun findByCacheKeyAndOfferIdAndExpiresAtAfter(cacheKey: String, offerId: String, now: Instant): Optional<RechargeOfferCacheEntity>
    fun deleteByCacheKey(cacheKey: String): Long
}

interface PaymentOrderRepository : JpaRepository<PaymentOrderEntity, Long> {
    fun findByClientRequestIdAndUserId(clientRequestId: String, userId: Long): Optional<PaymentOrderEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByRazorpayOrderIdAndUserId(razorpayOrderId: String, userId: Long): Optional<PaymentOrderEntity>
}


interface PasswordResetOtpRepository : JpaRepository<com.recharge.backend.domain.PasswordResetOtpEntity, Long> {
    fun findByMobile(mobile: String): java.util.Optional<com.recharge.backend.domain.PasswordResetOtpEntity>
}


interface AdminVendorRepository : JpaRepository<com.recharge.backend.domain.AdminVendorEntity, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<com.recharge.backend.domain.AdminVendorEntity>
    fun findAllByActiveTrueOrderByCreatedAtDesc(): List<com.recharge.backend.domain.AdminVendorEntity>
}
