package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.domain.RechargeTransactionEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.domain.WalletTransactionEntity
import com.recharge.backend.repository.*
import jakarta.transaction.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

@Service
class AdminService(
    private val users: UserRepository,
    private val wallets: WalletRepository,
    private val walletLedger: WalletTransactionRepository,
    private val recharges: RechargeTransactionRepository,
    private val withdrawals: WalletWithdrawalRepository,
    private val commissionRates: CommissionRateService,
    private val roleAccess: RoleAccessService,
    private val imageStorage: ProfileImageStorage
) {
    private val zoneId = ZoneId.of("Asia/Kolkata")

    fun users(requestedRole: String?, sort: String, viewer: UserEntity): List<AdminUserSummaryResponse> {
        roleAccess.requirePermission(viewer, "VIEW_USERS")
        val visibleRoles = roleAccess.visibleRolesFor(viewer.role)
        val roleFilter = requestedRole?.trim()?.uppercase()?.takeIf { it != "ALL" }
        if (roleFilter != null && roleFilter !in visibleRoles) return emptyList()
        val selectedRoles = if (roleFilter == null) visibleRoles else setOf(roleFilter)
        val records = users.findAllByRoleInOrderByCreatedAtDesc(selectedRoles.toList())
        if (records.isEmpty()) return emptyList()
        val userIds = records.map(::requireId)
        val now = ZonedDateTime.now(zoneId)
        val todayStart = now.toLocalDate().atStartOfDay(zoneId).toInstant()
        val tomorrowStart = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId).toInstant()
        val walletsByUserId = wallets.findAllByUserIds(userIds).associateBy { requireNotNull(it.user?.id) }
        val todayAggregates = recharges.aggregateSuccessfulForUsers(userIds, todayStart, tomorrowStart).associateBy { it.userId }
        val monthAggregates = recharges.aggregateSuccessfulForUsers(userIds, monthStart, now.toInstant().plusNanos(1)).associateBy { it.userId }
        val today = records.map { user ->
            val userId = requireId(user)
            toSummary(user, walletsByUserId[userId] ?: throw IllegalArgumentException("Wallet not found"), todayAggregates[userId], monthAggregates[userId])
        }
        return when (sort) {
            "today-low" -> today.sortedBy { it.todayEarnings }
            "month-high" -> today.sortedByDescending { it.monthEarnings }
            "month-low" -> today.sortedBy { it.monthEarnings }
            else -> today.sortedByDescending { it.todayEarnings }
        }
    }

    fun userDetail(viewer: UserEntity, targetPublicId: String): AdminUserDetailResponse {
        val target = resolveTarget(viewer, targetPublicId)

        val now = ZonedDateTime.now(zoneId)
        val todayStart = now.toLocalDate().atStartOfDay(zoneId).toInstant()
        val tomorrowStart = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId).toInstant()
        val targetId = requireId(target)
        val wallet = wallets.findByUserId(targetId).orElseThrow { IllegalArgumentException("Wallet not found") }
        val todayAggregate = recharges.aggregateSuccessfulForUsers(listOf(targetId), todayStart, tomorrowStart).firstOrNull()
        val monthAggregate = recharges.aggregateSuccessfulForUsers(listOf(targetId), monthStart, now.toInstant().plusNanos(1)).firstOrNull()
        val summary = toSummary(target, wallet, todayAggregate, monthAggregate)
        val rechargeCount = recharges.countSuccessfulByUserId(targetId)
        val addMoneyTotal = walletLedger.sumAddMoneyAllTime(targetId).setScale(2)
        val withdrawalTotal = walletLedger.sumWithdrawalsAllTime(targetId).setScale(2)
        val latest = recharges.findTopByUserIdOrderByCreatedAtDesc(targetId)
        val recentEntries = walletLedger.findTop10ByUserIdOrderByCreatedAtDesc(targetId).map(::toWalletEntry)
        val latestResponse = latest?.let(::toLatestRecharge)
        val imageVersion = target.profileImageUpdatedAt?.toEpochMilli()
        return AdminUserDetailResponse(
            summary = summary,
            rechargeCount = rechargeCount,
            addMoneyTotal = addMoneyTotal,
            withdrawalTotal = withdrawalTotal,
            commissionRate = commissionRates.rateForRole(target.role),
            balance = wallet.balance.setScale(2),
            availableBalance = wallet.balance.subtract(wallet.reservedBalance).max(BigDecimal.ZERO).setScale(2),
            reservedBalance = wallet.reservedBalance.setScale(2),
            profileImageUrl = target.profileImageKey?.let { "/api/v1/admin/users/" + target.publicId + "/profile-image" },
            profileImageVersion = imageVersion,
            latestRecharge = latestResponse,
            recentWalletEntries = recentEntries
        )
    }

    fun rechargeHistory(viewer: UserEntity, targetPublicId: String, page: Int, size: Int): RechargeHistoryResponse {
        val target = resolveTarget(viewer, targetPublicId)
        require(page >= 0) { "Page must be non-negative" }
        require(size in 1..50) { "Page size must be between 1 and 50" }
        val now = Instant.now()
        val pageData = recharges.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            requireId(target), Instant.EPOCH, now.plusNanos(1), PageRequest.of(page, size)
        )
        return RechargeHistoryResponse(
            items = pageData.content.map {
                RechargeHistoryItem(
                    transactionId = it.transactionId,
                    clientRequestId = it.clientRequestId,
                    mobileNumber = it.mobileNumber,
                    operator = it.operator,
                    circle = it.circle,
                    planId = it.planId,
                    planDescription = it.planDescription,
                    planValidity = it.planValidity,
                    amount = it.amount.setScale(2),
                    walletDebitAmount = it.walletDebitAmount.setScale(2),
                    status = it.status,
                    provider = it.providerName,
                    providerReference = it.providerReference,
                    providerOrderId = it.providerOrderId,
                    walletLedgerRef = it.walletLedgerRef,
                    completedAt = it.completedAt,
                    clientCommission = it.clientCommission.setScale(2),
                    companyCommission = it.companyCommission.setScale(2),
                    message = it.message,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            page = pageData.number,
            size = pageData.size,
            totalItems = pageData.totalElements,
            totalPages = pageData.totalPages,
            hasNext = pageData.hasNext(),
            fromDate = "1970-01-01",
            toDate = java.time.LocalDate.now(zoneId).toString()
        )
    }

    fun walletHistory(viewer: UserEntity, targetPublicId: String, page: Int, size: Int): WalletHistoryResponse {
        val target = resolveTarget(viewer, targetPublicId)
        require(page >= 0) { "Page must be non-negative" }
        require(size in 1..50) { "Page size must be between 1 and 50" }
        val now = Instant.now()
        val pageData = walletLedger.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            requireId(target), Instant.EPOCH, now.plusNanos(1), PageRequest.of(page, size)
        )
        val rechargeIds = pageData.content
            .filter { it.referenceType.equals("RECHARGE", true) && !it.referenceId.isNullOrBlank() }
            .mapNotNull { it.referenceId }
            .distinct()
        val rechargeById = if (rechargeIds.isEmpty()) emptyMap() else recharges.findAllByTransactionIdIn(rechargeIds).associateBy { it.transactionId }
        return WalletHistoryResponse(
            items = pageData.content.map { tx ->
                val recharge = tx.referenceId?.let(rechargeById::get)
                WalletHistoryItem(
                    id = tx.id ?: 0L,
                    type = tx.type,
                    amount = tx.amount.setScale(2),
                    status = tx.status,
                    referenceType = tx.referenceType,
                    referenceId = tx.referenceId,
                    externalRef = tx.externalRef,
                    description = tx.description,
                    createdAt = tx.createdAt,
                    mobileNumber = recharge?.mobileNumber,
                    operator = recharge?.operator,
                    circle = recharge?.circle
                )
            },
            page = pageData.number,
            size = pageData.size,
            totalItems = pageData.totalElements,
            totalPages = pageData.totalPages,
            hasNext = pageData.hasNext(),
            fromDate = "1970-01-01",
            toDate = java.time.LocalDate.now(zoneId).toString()
        )
    }

    fun withdrawalHistory(viewer: UserEntity, targetPublicId: String, page: Int, size: Int): WithdrawalHistoryResponse {
        val target = resolveTarget(viewer, targetPublicId)
        require(page >= 0) { "Page must be non-negative" }
        require(size in 1..50) { "Page size must be between 1 and 50" }
        val pageData = withdrawals.findByUserIdOrderByCreatedAtDesc(
            requireId(target), PageRequest.of(page, size)
        )
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

    fun profileImage(viewer: UserEntity, targetPublicId: String, variant: ImageVariant): ProfileImageStorage.StoredImage {
        val target = resolveTarget(viewer, targetPublicId)
        val key = target.profileImageKey ?: throw IllegalArgumentException("Profile image not found")
        return imageStorage.load(key, variant) ?: throw IllegalArgumentException("Profile image not found")
    }

    private fun resolveTarget(viewer: UserEntity, targetPublicId: String): UserEntity {
        val target = users.findByPublicId(targetPublicId).orElse(null)
            ?: targetPublicId.toLongOrNull()?.let { users.findById(it).orElse(null) }
            ?: throw IllegalArgumentException("User not found")
        roleAccess.requireCanView(viewer, target)
        return target
    }

    fun dashboard(viewer: UserEntity): AdminDashboardResponse {
        roleAccess.requirePermission(viewer, "VIEW_DASHBOARD")
        val visibleRoles = roleAccess.visibleRolesFor(viewer.role)
        val userSet = users.findAllByRoleIn(visibleRoles.toList())
        val userIds = userSet.mapNotNull { it.id }
        if (userIds.isEmpty()) {
            val now = ZonedDateTime.now(zoneId)
            val chart = (0L..6L).map { offset ->
                val day = now.toLocalDate().minusDays(6L - offset)
                AdminDashboardChartPoint(day.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM")), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2))
            }
            return AdminDashboardResponse(
                todayVolume = BigDecimal.ZERO.setScale(2), todayCommission = BigDecimal.ZERO.setScale(2),
                monthlyVolume = BigDecimal.ZERO.setScale(2), monthlyCommission = BigDecimal.ZERO.setScale(2),
                activeClients = 0, successfulRecharges = 0L, totalUsers = 0L, chart = chart,
                from = now.toLocalDate().atStartOfDay(zoneId).toInstant(), to = now.toInstant(),
                monthFrom = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId).toInstant(), monthTo = now.toInstant()
            )
        }
        val now = ZonedDateTime.now(zoneId)
        val todayStart = now.toLocalDate().atStartOfDay(zoneId)
        val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId)
        val nowInstant = now.toInstant()
        val todayEnd = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        val monthStartInstant = monthStart.toInstant()
        val todayStartInstant = todayStart.toInstant()
        val todayVolume = recharges.sumAmountForUsers(userIds, todayStartInstant, todayEnd).setScale(2)
        val todayCommission = recharges.sumCompanyCommissionForUsers(userIds, todayStartInstant, todayEnd).setScale(2)
        val monthlyVolume = recharges.sumAmountForUsers(userIds, monthStartInstant, nowInstant.plusNanos(1)).setScale(2)
        val monthlyCommission = recharges.sumCompanyCommissionForUsers(userIds, monthStartInstant, nowInstant.plusNanos(1)).setScale(2)
        val successfulRecharges = recharges.countSuccessfulForUsers(userIds, todayStartInstant, todayEnd)
        val activeClients = userSet.count { it.role.equals("CLIENT", true) && it.active }
        val chart = (0L..6L).map { offset ->
            val day = now.toLocalDate().minusDays(6L - offset)
            val from = day.atStartOfDay(zoneId).toInstant()
            val to = day.plusDays(1).atStartOfDay(zoneId).toInstant()
            AdminDashboardChartPoint(
                label = day.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM")),
                volume = recharges.sumAmountForUsers(userIds, from, to).setScale(2),
                commission = recharges.sumCompanyCommissionForUsers(userIds, from, to).setScale(2)
            )
        }
        return AdminDashboardResponse(
            todayVolume = todayVolume,
            todayCommission = todayCommission,
            monthlyVolume = monthlyVolume,
            monthlyCommission = monthlyCommission,
            activeClients = activeClients,
            successfulRecharges = successfulRecharges,
            totalUsers = userSet.size.toLong(),
            chart = chart,
            from = todayStartInstant,
            to = nowInstant,
            monthFrom = monthStartInstant,
            monthTo = nowInstant
        )
    }

    @Transactional
    fun updateUserStatus(viewer: UserEntity, targetPublicId: String, active: Boolean): AdminUserStatusResponse {
        roleAccess.requirePermission(viewer, "MANAGE_USER_STATUS")
        val target = resolveTarget(viewer, targetPublicId)
        require(requireId(target) != requireId(viewer)) { "You cannot change your own account status" }
        require(!target.role.equals("ADMIN", true)) { "Admin accounts cannot be deactivated from the portal" }

        target.active = active
        users.save(target)
        return AdminUserStatusResponse(
            publicUserId = target.publicId,
            active = target.active,
            status = if (target.active) "ACTIVE" else "BLOCKED"
        )
    }

    private fun toSummary(
        user: UserEntity,
        wallet: com.recharge.backend.domain.WalletEntity,
        today: UserRechargeSummaryProjection?,
        month: UserRechargeSummaryProjection?
    ): AdminUserSummaryResponse {
        val todayData = today ?: zeroRechargeAggregate()
        val monthData = month ?: zeroRechargeAggregate()
        return AdminUserSummaryResponse(
            id = user.publicId,
            publicUserId = user.publicId,
            name = user.name ?: "Unnamed user",
            mobile = user.mobile,
            email = user.email ?: "",
            role = user.role.uppercase(),
            accountType = user.role.lowercase().replaceFirstChar { it.uppercase() },
            todayEarnings = todayData.clientCommission.setScale(2),
            monthEarnings = monthData.clientCommission.setScale(2),
            todayVolume = todayData.amount.setScale(2),
            monthVolume = monthData.amount.setScale(2),
            walletBalance = wallet.balance.setScale(2),
            joinedAt = user.createdAt,
            profileUpdatedAt = user.profileUpdatedAt,
            status = if (user.active) "ACTIVE" else "BLOCKED"
        )
    }

    private fun zeroRechargeAggregate() = object : UserRechargeSummaryProjection {
        override val userId: Long = -1L
        override val clientCommission: BigDecimal = BigDecimal.ZERO
        override val amount: BigDecimal = BigDecimal.ZERO
        override val successfulCount: Long = 0L
    }

    private fun toWalletEntry(tx: WalletTransactionEntity) = AdminWalletEntryResponse(
        id = requireNotNull(tx.id).toString(),
        type = when (tx.referenceType?.uppercase()) {
            "RECHARGE" -> "RECHARGE"
            "ADD_MONEY" -> "ADD_MONEY"
            "WITHDRAWAL" -> "WITHDRAWAL"
            else -> tx.referenceType?.uppercase() ?: tx.type
        },
        amount = tx.amount.setScale(2),
        createdAt = tx.createdAt,
        reference = tx.referenceId ?: tx.externalRef
    )

    private fun toLatestRecharge(tx: RechargeTransactionEntity) = AdminLatestRechargeResponse(
        mobile = tx.mobileNumber,
        operator = tx.operator,
        amount = tx.amount.setScale(2),
        commission = tx.clientCommission.setScale(2),
        status = tx.status,
        createdAt = tx.createdAt,
        transactionId = tx.transactionId
    )

    private fun requireId(user: UserEntity): Long = requireNotNull(user.id)
}
