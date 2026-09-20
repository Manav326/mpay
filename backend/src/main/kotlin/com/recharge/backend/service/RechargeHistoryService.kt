package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.domain.RechargeTransactionEntity
import com.recharge.backend.repository.RechargeTransactionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.*

@Service
class RechargeHistoryService(
    private val repository: RechargeTransactionRepository,
    private val commissionRateService: CommissionRateService
) {
    private val zoneId: ZoneId = ZoneId.of("Asia/Kolkata")

    fun history(userId: Long, page: Int, size: Int, fromDate: LocalDate?, toDate: LocalDate?): RechargeHistoryResponse {
        require(page >= 0) { "Page must be zero or greater" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
        val now = ZonedDateTime.now(zoneId)
        val from = fromDate ?: now.toLocalDate()
        val to = (toDate ?: from).takeIf { !it.isBefore(from) } ?: throw IllegalArgumentException("toDate must be on or after fromDate")
        val fromInstant = from.atStartOfDay(zoneId).toInstant()
        val toExclusive = to.plusDays(1).atStartOfDay(zoneId).toInstant()
        val result = repository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, fromInstant, toExclusive.minusNanos(1), PageRequest.of(page, size))
        return RechargeHistoryResponse(
            items = result.content.map(::toItem),
            page = result.number,
            size = result.size,
            totalItems = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext(),
            fromDate = from.toString(),
            toDate = to.toString()
        )
    }

    fun commissionSummary(userId: Long): RechargeCommissionSummaryResponse {
        val now = ZonedDateTime.now(zoneId)
        val todayStart = now.toLocalDate().atStartOfDay(zoneId)
        val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId)
        val nowInstant = now.toInstant()

        fun period(from: ZonedDateTime, to: Instant): CommissionPeriodSummary = CommissionPeriodSummary(
            from = from.toInstant(),
            to = to,
            commission = repository.sumClientCommission(userId, from.toInstant(), to).setScale(2),
            successfulRechargeAmount = repository.sumSuccessfulRechargeAmount(userId, from.toInstant(), to).setScale(2),
            successfulRechargeCount = repository.countSuccessfulRecharges(userId, from.toInstant(), to)
        )

        return RechargeCommissionSummaryResponse(
            commissionPercent = commissionRateService.rateForUser(userId),
            daily = period(todayStart, nowInstant),
            monthly = period(monthStart, nowInstant)
        )
    }

    private fun toItem(tx: RechargeTransactionEntity): RechargeHistoryItem = RechargeHistoryItem(
        transactionId = tx.transactionId, clientRequestId = tx.clientRequestId, mobileNumber = tx.mobileNumber,
        operator = tx.operator, circle = tx.circle, planId = tx.planId, planDescription = tx.planDescription,
        planValidity = tx.planValidity, amount = tx.amount, walletDebitAmount = tx.walletDebitAmount, status = tx.status, provider = tx.providerName,
        providerReference = tx.providerReference, providerOrderId = tx.providerOrderId, walletLedgerRef = tx.walletLedgerRef,
        completedAt = tx.completedAt, clientCommission = tx.clientCommission, companyCommission = tx.companyCommission,
        message = tx.message, createdAt = tx.createdAt, updatedAt = tx.updatedAt
    )
}
