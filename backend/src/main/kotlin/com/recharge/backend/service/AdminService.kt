package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.domain.AdminVendorEntity
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
    private val vendors: AdminVendorRepository,
    private val commissionRates: CommissionRateService,
    private val roleAccess: RoleAccessService
) {
    private val zoneId = ZoneId.of("Asia/Kolkata")

    fun users(requestedRole: String?, sort: String, viewer: UserEntity): List<AdminUserSummaryResponse> {
        roleAccess.requirePermission(viewer, "VIEW_USERS")
        val visibleRoles = roleAccess.visibleRolesFor(viewer.role)
        val roleFilter = requestedRole?.trim()?.uppercase()?.takeIf { it != "ALL" }
        if (roleFilter != null && roleFilter !in visibleRoles) return emptyList()
        val selectedRoles = if (roleFilter == null) visibleRoles else setOf(roleFilter)
        val records = users.findAllByRoleInOrderByCreatedAtDesc(selectedRoles.toList())
        val now = ZonedDateTime.now(zoneId)
        val todayStart = now.toLocalDate().atStartOfDay(zoneId).toInstant()
        val tomorrowStart = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId).toInstant()
        val today = records.map { toSummary(it, todayStart, tomorrowStart, monthStart, now.toInstant()) }
        return when (sort) {
            "today-low" -> today.sortedBy { it.todayEarnings }
            "month-high" -> today.sortedByDescending { it.monthEarnings }
            "month-low" -> today.sortedBy { it.monthEarnings }
            else -> today.sortedByDescending { it.todayEarnings }
        }
    }

    fun userDetail(viewer: UserEntity, targetPublicId: String): AdminUserDetailResponse {
        val target = users.findByPublicId(targetPublicId).orElse(null)
            ?: targetPublicId.toLongOrNull()?.let { users.findById(it).orElse(null) }
            ?: throw IllegalArgumentException("User not found")
        roleAccess.requireCanView(viewer, target)

        val now = ZonedDateTime.now(zoneId)
        val todayStart = now.toLocalDate().atStartOfDay(zoneId).toInstant()
        val tomorrowStart = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId).toInstant()
        val summary = toSummary(target, todayStart, tomorrowStart, monthStart, now.toInstant())
        val rechargeCount = recharges.countSuccessfulByUserId(requireId(target))
        val addMoneyTotal = walletLedger.sumAddMoneyAllTime(requireId(target)).setScale(2)
        val withdrawalTotal = walletLedger.sumWithdrawalsAllTime(requireId(target)).setScale(2)
        val latest = recharges.findTopByUserIdOrderByCreatedAtDesc(requireId(target))
        val recentEntries = walletLedger.findTop10ByUserIdOrderByCreatedAtDesc(requireId(target)).map(::toWalletEntry)
        val latestResponse = latest?.let(::toLatestRecharge)
        return AdminUserDetailResponse(
            summary = summary,
            rechargeCount = rechargeCount,
            addMoneyTotal = addMoneyTotal,
            withdrawalTotal = withdrawalTotal,
            commissionRate = commissionRates.rateForRole(target.role),
            latestRecharge = latestResponse,
            recentWalletEntries = recentEntries
        )
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

    fun vendorList(viewer: UserEntity): List<AdminVendorResponse> {
        roleAccess.requirePermission(viewer, "MANAGE_VENDORS")
        return vendors.findAllByOrderByCreatedAtDesc().map(::toVendor)
    }

    @Transactional
    fun createVendor(viewer: UserEntity, request: CreateAdminVendorRequest): AdminVendorResponse {
        roleAccess.requirePermission(viewer, "MANAGE_VENDORS")
        val category = request.category.trim().uppercase()
        require(category in setOf("CAR_RENT", "TRAVEL", "SERVICES")) { "Unsupported vendor category" }
        require(request.name.trim().isNotBlank()) { "Vendor name is required" }
        require(request.city.trim().isNotBlank()) { "Vendor city is required" }
        require(request.phone.trim().matches(Regex("[0-9+ -]{7,20}"))) { "Vendor phone is invalid" }
        require(request.commissionRate >= BigDecimal.ZERO && request.commissionRate < BigDecimal(100)) { "Vendor commission rate must be between 0 and 100" }
        val now = Instant.now()
        val saved = vendors.save(
            AdminVendorEntity(
                name = request.name.trim(),
                category = category,
                city = request.city.trim(),
                phone = request.phone.trim(),
                commissionRate = request.commissionRate.setScale(2, RoundingMode.HALF_UP),
                active = request.active,
                createdAt = now,
                updatedAt = now
            )
        )
        return toVendor(saved)
    }

    private fun toSummary(user: UserEntity, todayStart: Instant, todayEnd: Instant, monthStart: Instant, now: Instant): AdminUserSummaryResponse {
        val userId = requireId(user)
        val wallet = wallets.findByUserId(userId).orElseThrow { IllegalArgumentException("Wallet not found") }
        return AdminUserSummaryResponse(
            id = user.publicId,
            publicUserId = user.publicId,
            name = user.name ?: "Unnamed user",
            mobile = user.mobile,
            email = user.email ?: "",
            role = user.role.uppercase(),
            accountType = user.role.lowercase().replaceFirstChar { it.uppercase() },
            todayEarnings = recharges.sumClientCommission(userId, todayStart, todayEnd).setScale(2),
            monthEarnings = recharges.sumClientCommission(userId, monthStart, now.plusNanos(1)).setScale(2),
            todayVolume = recharges.sumSuccessfulRechargeAmount(userId, todayStart, todayEnd).setScale(2),
            monthVolume = recharges.sumSuccessfulRechargeAmount(userId, monthStart, now.plusNanos(1)).setScale(2),
            walletBalance = wallet.balance.setScale(2),
            joinedAt = user.createdAt,
            profileUpdatedAt = user.profileUpdatedAt,
            status = if (user.active) "ACTIVE" else "BLOCKED"
        )
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

    private fun toVendor(vendor: AdminVendorEntity) = AdminVendorResponse(
        id = requireNotNull(vendor.id).toString(), name = vendor.name, category = vendor.category,
        city = vendor.city, phone = vendor.phone, commissionRate = vendor.commissionRate.setScale(2),
        active = vendor.active, createdAt = vendor.createdAt
    )

    private fun requireId(user: UserEntity): Long = requireNotNull(user.id)
}
