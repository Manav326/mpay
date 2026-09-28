package com.recharge.backend.repository

import com.recharge.backend.domain.HistoryPdfAccessRequestEntity
import org.springframework.data.jpa.repository.JpaRepository

interface HistoryPdfAccessRequestRepository : JpaRepository<HistoryPdfAccessRequestEntity, Long> {
    fun findTopByUserIdAndFeatureKeyOrderByRequestedAtDesc(userId: Long, featureKey: String): HistoryPdfAccessRequestEntity?
    fun findTop50ByFeatureKeyAndStatusOrderByRequestedAtAsc(featureKey: String, status: String): List<HistoryPdfAccessRequestEntity>
}
