package com.recharge.backend.service

import com.recharge.backend.repository.RechargeTransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "app.recharge.reconciliation", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class RechargeReconciliationService(
    private val recharges: RechargeTransactionRepository,
    private val rechargeService: RechargeService,
    @Value("\${app.recharge.reconciliation.batch-size:25}") private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        initialDelayString = "\${app.recharge.reconciliation.initial-delay-ms:30000}",
        fixedDelayString = "\${app.recharge.reconciliation.interval-ms:60000}"
    )
    fun reconcileOpenRecharges() {
        val candidates = recharges.findByStatusInAndProviderReferenceIsNotNullOrderByUpdatedAtAsc(
            listOf("PENDING", "RESERVED"),
            PageRequest.of(0, batchSize.coerceIn(1, 100))
        )
        candidates.forEach { tx ->
            try {
                rechargeService.transaction(tx.userId, tx.transactionId)
            } catch (ex: Exception) {
                log.warn("Recharge reconciliation check failed for transactionId={}", tx.transactionId, ex)
            }
        )
    }
}
