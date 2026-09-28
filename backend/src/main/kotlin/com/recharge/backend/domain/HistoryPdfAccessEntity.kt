package com.recharge.backend.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "history_pdf_access_requests",
    indexes = [
        Index(name = "idx_history_pdf_access_user_status", columnList = "user_id,status"),
        Index(name = "idx_history_pdf_access_status_requested", columnList = "status,requested_at")
    ]
)
class HistoryPdfAccessRequestEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "user_id", nullable = false) var userId: Long = 0,
    @Column(name = "feature_key", nullable = false, length = 60) var featureKey: String = "HISTORY_PDF_EXPORT",
    @Column(nullable = false, length = 20) var status: String = "PENDING",
    @Column(name = "request_reason", nullable = false, length = 1000) var requestReason: String = "",
    @Column(name = "review_note", length = 1000) var reviewNote: String? = null,
    @Column(name = "reviewed_by") var reviewedBy: Long? = null,
    @Column(name = "requested_at", nullable = false) var requestedAt: Instant = Instant.now(),
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null
)
