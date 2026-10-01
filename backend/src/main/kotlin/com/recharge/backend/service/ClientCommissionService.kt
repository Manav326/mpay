package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.domain.ClientCommissionSettingsEntity
import com.recharge.backend.domain.ClientReferralLinkEntity
import com.recharge.backend.domain.ClientUpstreamCommissionEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.domain.RechargeTransactionEntity
import com.recharge.backend.repository.ClientCommissionSettingsRepository
import com.recharge.backend.repository.ClientReferralLinkRepository
import com.recharge.backend.repository.ClientUpstreamCommissionRepository
import com.recharge.backend.repository.RechargeTransactionRepository
import com.recharge.backend.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Service
class ClientCommissionService(
    private val users: UserRepository,
    private val rechargeTransactions: RechargeTransactionRepository,
    private val referrals: ClientReferralLinkRepository,
    private val upstreamCommissions: ClientUpstreamCommissionRepository,
    private val settingsRepository: ClientCommissionSettingsRepository,
    private val commissionRates: CommissionRateService,
    private val wallet: WalletService
) {
    private val zoneId: ZoneId = ZoneId.of("Asia/Kolkata")

    fun overview(userId: Long): ClientCommissionOverviewResponse {
        val user = requireClient(userId)
        val settings = settings()
        val directCount = referrals.countByParentUserId(userId)
        val level = levelFor(userId, directCount, settings.level2DirectClientThreshold)
        val parent = referrals.findByChildUserId(userId).orElse(null)
        val now = Instant.now()
        val todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
        val monthStart = LocalDate.now(zoneId).withDayOfMonth(1).atStartOfDay(zoneId).toInstant()
        return ClientCommissionOverviewResponse(
            publicUserId = user.publicId,
            level = level,
            baseCommissionPercent = commissionRates.rateForUser(userId),
            directClientCount = directCount,
            level2DirectClientThreshold = settings.level2DirectClientThreshold,
            level2Qualified = level >= 2,
            canAddClients = level >= 1,
            upstreamCommissionPercent = settings.upstreamCommissionPercent.setScale(2),
            upstreamCommissionActive = settings.upstreamCommissionActive,
            upstreamEligible = level >= 2 && settings.upstreamCommissionActive && settings.upstreamCommissionPercent.signum() > 0,
            parent = parent?.let { memberFor(it.parentUserId, it.childUserId, it.assignedAt) },
            todayUpstreamCommission = upstreamCommissions.sumCommission(userId, todayStart, now).setScale(2),
            monthUpstreamCommission = upstreamCommissions.sumCommission(userId, monthStart, now).setScale(2)
        )
    }

    fun directClients(userId: Long): List<ClientReferralMemberResponse> {
        requireClient(userId)
        val links = referrals.findAllByParentUserIdOrderByAssignedAtAsc(userId)
        if (links.isEmpty()) return emptyList()
        val usersById = users.findAllById(links.map { it.childUserId }).associateBy { it.id }
        return links.mapNotNull { link ->
            usersById[link.childUserId]?.let { child ->
                ClientReferralMemberResponse(
                    publicUserId = child.publicId,
                    name = child.name,
                    mobile = child.mobile,
                    assignedAt = link.assignedAt
                )
            }
        }
    }

    fun searchEligibleClients(userId: Long, query: String): List<ClientSearchResultResponse> {
        val parent = requireClient(userId)
        require(levelFor(userId) >= 1) { "Complete at least one recharge attempt before adding clients" }
        val normalized = query.trim()
        require(normalized.length >= 3) { "Enter at least 3 characters to search for a client" }
        val candidates = users.searchSupportCustomers(normalized, PageRequest.of(0, 30))
        if (candidates.isEmpty()) return emptyList()
        val candidateIds = candidates.mapNotNull { it.id }.filter { it != parent.id }
        val assignedIds = referrals.findAllByChildUserIdIn(candidateIds).mapTo(mutableSetOf()) { it.childUserId }
        return candidates.asSequence()
            .filter { it.id != parent.id }
            .filter { it.role.equals("CLIENT", true) && it.active && it.deletedAt == null && it.mobileVerifiedAt != null }
            .filter { it.id !in assignedIds }
            .take(10)
            .map { ClientSearchResultResponse(it.publicId, it.name, it.mobile) }
            .toList()
    }

    @Transactional
    fun addClient(parentUserId: Long, childPublicId: String): ClientReferralMemberResponse {
        val parent = requireClient(parentUserId)
        require(levelFor(parentUserId) >= 1) { "Complete at least one recharge attempt before adding clients" }
        val child = childPublicId.trim().takeIf { it.isNotBlank() }?.let {
            users.findByPublicId(it).orElseThrow { IllegalArgumentException("Client not found") }
        } ?: throw IllegalArgumentException("Client ID is required")
        val lockedChild = users.findByIdForUpdate(requireNotNull(child.id)).orElseThrow { IllegalArgumentException("Client not found") }
        require(lockedChild.role.equals("CLIENT", true)) { "Only client accounts can be added to the commission network" }
        require(lockedChild.active && lockedChild.deletedAt == null) { "This client account is not active" }
        require(lockedChild.mobileVerifiedAt != null) { "Only verified clients can be added" }
        require(lockedChild.id != parent.id) { "A client cannot add itself" }
        require(referrals.findByChildUserId(requireNotNull(lockedChild.id)).isEmpty) {
            "This client is already assigned under another client"
        }
        ensureNoHierarchyCycle(parent.id!!, lockedChild.id!!)

        val link = try {
            referrals.save(
                ClientReferralLinkEntity(
                    parentUserId = parent.id!!,
                    childUserId = lockedChild.id!!,
                    assignedAt = Instant.now()
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            throw IllegalArgumentException("This client is already assigned under another client", ex)
        }
        return ClientReferralMemberResponse(
            publicUserId = lockedChild.publicId,
            name = lockedChild.name,
            mobile = lockedChild.mobile,
            assignedAt = link.assignedAt
        )
    }

    fun upstreamHistory(userId: Long, page: Int, size: Int): ClientUpstreamCommissionPageResponse {
        requireClient(userId)
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
        val result = upstreamCommissions.findByParentUserIdOrderByCreatedAtDesc(
            userId,
            PageRequest.of(page, size)
        )
        val childIds = result.content.map { it.childUserId }.distinct()
        val usersById = users.findAllById(childIds).associateBy { it.id }
        return ClientUpstreamCommissionPageResponse(
            items = result.content.map { item ->
                val child = usersById[item.childUserId]
                ClientUpstreamCommissionHistoryItem(
                    childPublicUserId = child?.publicId ?: "—",
                    childName = child?.name,
                    childMobile = child?.mobile ?: "—",
                    rechargeTransactionId = item.rechargeTransactionId,
                    rechargeAmount = item.rechargeAmount.setScale(2),
                    commissionPercent = item.commissionPercent.setScale(2),
                    commissionAmount = item.commissionAmount.setScale(2),
                    walletLedgerRef = item.walletLedgerRef,
                    createdAt = item.createdAt
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
    fun creditUpstreamCommission(recharge: RechargeTransactionEntity): BigDecimal? {
        val link = referrals.findByChildUserId(recharge.userId).orElse(null) ?: return null
        val parentId = link.parentUserId
        if (parentId == recharge.userId) return null

        val settings = settings()
        if (!settings.upstreamCommissionActive || settings.upstreamCommissionPercent.signum() <= 0) return null
        val directCount = referrals.countByParentUserId(parentId)
        if (directCount < settings.level2DirectClientThreshold || !rechargeUserQualifies(recharge.userId)) return null

        if (upstreamCommissions.existsByRechargeTransactionId(recharge.transactionId)) {
            return null
        }

        val amount = recharge.amount
            .multiply(settings.upstreamCommissionPercent)
            .divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP)
        if (amount.signum() <= 0) return null

        val externalRef = "UPSTREAM_COMMISSION:" + recharge.transactionId
        val ledgerBalance = wallet.credit(
            userId = parentId,
            amount = amount,
            externalRef = externalRef,
            referenceType = "UPSTREAM_COMMISSION",
            referenceId = recharge.transactionId,
            description = "Upstream commission from client recharge " + recharge.transactionId
        )
        upstreamCommissions.save(
            ClientUpstreamCommissionEntity(
                parentUserId = parentId,
                childUserId = recharge.userId,
                rechargeTransactionId = recharge.transactionId,
                rechargeAmount = recharge.amount.setScale(2),
                commissionPercent = settings.upstreamCommissionPercent.setScale(4),
                commissionAmount = amount,
                walletLedgerRef = externalRef,
                createdAt = Instant.now()
            )
        )
        return ledgerBalance
    }

    fun clientCommissionPercent(): BigDecimal = commissionRates.rateForRole("CLIENT")

    fun settings(): ClientCommissionSettingsEntity =
        settingsRepository.findById(1L).orElseGet {
            settingsRepository.save(ClientCommissionSettingsEntity(id = 1L))
        }

    @Transactional
    fun updateSettings(
        level2DirectClientThreshold: Int,
        upstreamCommissionPercent: BigDecimal,
        upstreamCommissionActive: Boolean
    ): ClientCommissionSettingsResponse {
        require(level2DirectClientThreshold >= 1) { "Level 2 requires at least one direct client" }
        require(upstreamCommissionPercent >= BigDecimal.ZERO && upstreamCommissionPercent < BigDecimal(100)) {
            "Upstream commission percent must be between 0 and 100"
        }
        val entity = settings()
        entity.level2DirectClientThreshold = level2DirectClientThreshold
        entity.upstreamCommissionPercent = upstreamCommissionPercent.setScale(4)
        entity.upstreamCommissionActive = upstreamCommissionActive
        entity.updatedAt = Instant.now()
        val saved = settingsRepository.save(entity)
        return settingsResponse(saved)
    }

    fun settingsResponse(): ClientCommissionSettingsResponse = settingsResponse(settings())

    private fun settingsResponse(entity: ClientCommissionSettingsEntity) = ClientCommissionSettingsResponse(
        level2DirectClientThreshold = entity.level2DirectClientThreshold,
        upstreamCommissionPercent = entity.upstreamCommissionPercent.setScale(2),
        upstreamCommissionActive = entity.upstreamCommissionActive
    )

    private fun requireClient(userId: Long): UserEntity {
        val user = users.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        require(user.role.equals("CLIENT", true)) { "This commission program is available only to client accounts" }
        return user
    }

    private fun levelFor(userId: Long, directCount: Int? = null, threshold: Int = settings().level2DirectClientThreshold): Int {
        if (!rechargeUserQualifies(userId)) return 0
        val count = directCount ?: referrals.countByParentUserId(userId)
        return if (count >= threshold) 2 else 1
    }

    private fun rechargeUserQualifies(userId: Long): Boolean = rechargeTransactions.existsByUserId(userId)

    private fun ensureNoHierarchyCycle(parentUserId: Long, childUserId: Long) {
        var cursor = parentUserId
        val visited = mutableSetOf<Long>()
        while (visited.add(cursor)) {
            if (cursor == childUserId) {
                throw IllegalArgumentException("This assignment would create a client hierarchy cycle")
            }
            val link = referrals.findByChildUserId(cursor).orElse(null) ?: return
            cursor = link.parentUserId
        }
        throw IllegalArgumentException("Client hierarchy is inconsistent")
    }

    private fun memberFor(parentUserId: Long, childUserId: Long, assignedAt: Instant): ClientReferralMemberResponse {
        val child = users.findById(childUserId).orElseThrow { IllegalArgumentException("Client not found") }
        return ClientReferralMemberResponse(
            publicUserId = child.publicId,
            name = child.name,
            mobile = child.mobile,
            assignedAt = assignedAt
        )
    }
}
