package com.recharge.backend.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "support_case_events",
    indexes = [
        Index(name = "idx_support_case_events_customer_created", columnList = "customer_user_id,created_at"),
        Index(name = "idx_support_case_events_case_created", columnList = "case_id,created_at")
    ]
)
class SupportCaseEventEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "event_id", nullable = false, unique = true, length = 40) var eventId: String = UUID.randomUUID().toString(),
    @Column(name = "case_id", nullable = false) var caseId: Long = 0,
    @Column(name = "conversation_id") var conversationId: Long? = null,
    @Column(name = "customer_user_id", nullable = false) var customerUserId: Long = 0,
    @Column(name = "actor_user_id") var actorUserId: Long? = null,
    @Column(name = "event_type", nullable = false, length = 60) var eventType: String = "CASE_EVENT",
    @Column(nullable = false, length = 20) var visibility: String = "CUSTOMER",
    @Column(length = 30) var channel: String? = null,
    @Column(length = 500) var summary: String = "",
    @Column(length = 5000) var metadata: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
