package com.recharge.backend.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "support_cases",
    indexes = [
        Index(name = "idx_support_cases_customer_created", columnList = "customer_user_id,created_at"),
        Index(name = "idx_support_cases_status_updated", columnList = "status,updated_at")
    ]
)
class SupportCaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "case_id", nullable = false, unique = true, length = 40) var caseId: String = UUID.randomUUID().toString(),
    @Column(name = "customer_user_id", nullable = false) var customerUserId: Long = 0,
    @Column(nullable = false, length = 240) var subject: String = "",
    @Column(nullable = false, length = 80) var category: String = "GENERAL",
    @Column(nullable = false, length = 20) var priority: String = "NORMAL",
    @Column(nullable = false, length = 30) var status: String = "OPEN",
    @Column(nullable = false, length = 40) var source: String = "SUPPORT",
    @Column(name = "assigned_user_id") var assignedUserId: Long? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
    @Column(name = "resolution_code", length = 100) var resolutionCode: String? = null,
    @Column(name = "resolution_note", length = 1200) var resolutionNote: String? = null
)

@Entity
@Table(
    name = "support_conversations",
    indexes = [
        Index(name = "idx_support_conversations_customer_activity", columnList = "customer_user_id,last_activity_at"),
        Index(name = "idx_support_conversations_case", columnList = "case_id")
    ]
)
class SupportConversationEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "conversation_id", nullable = false, unique = true, length = 40) var conversationId: String = UUID.randomUUID().toString(),
    @Column(name = "case_id") var caseId: Long? = null,
    @Column(name = "customer_user_id", nullable = false) var customerUserId: Long = 0,
    @Column(nullable = false, length = 30) var status: String = "OPEN",
    @Column(name = "started_at", nullable = false) var startedAt: Instant = Instant.now(),
    @Column(name = "last_activity_at", nullable = false) var lastActivityAt: Instant = Instant.now(),
    @Column(name = "closed_at") var closedAt: Instant? = null
)

@Entity
@Table(
    name = "support_interactions",
    indexes = [
        Index(name = "idx_support_interactions_customer_started", columnList = "customer_user_id,started_at"),
        Index(name = "idx_support_interactions_conversation_started", columnList = "conversation_id,started_at")
    ]
)
class SupportInteractionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "interaction_id", nullable = false, unique = true, length = 40) var interactionId: String = UUID.randomUUID().toString(),
    @Column(name = "conversation_id", nullable = false) var conversationId: Long = 0,
    @Column(name = "case_id") var caseId: Long? = null,
    @Column(name = "customer_user_id", nullable = false) var customerUserId: Long = 0,
    @Column(name = "actor_user_id") var actorUserId: Long? = null,
    @Column(nullable = false, length = 30) var channel: String = "VOICE",
    @Column(nullable = false, length = 20) var direction: String = "OUTBOUND",
    @Column(nullable = false, length = 40) var status: String = "RINGING",
    @Column(name = "started_at", nullable = false) var startedAt: Instant = Instant.now(),
    @Column(name = "ended_at") var endedAt: Instant? = null,
    @Column(name = "duration_seconds") var durationSeconds: Long? = null,
    @Column(length = 120) var outcome: String? = null,
    @Column(name = "voice_call_id", unique = true, length = 40) var voiceCallId: String? = null,
    @Column(name = "chat_thread_id", length = 80) var chatThreadId: String? = null,
    @Column(name = "ring_duration_seconds") var ringDurationSeconds: Long? = null,
    @Column(name = "handling_duration_seconds") var handlingDurationSeconds: Long? = null,
    @Column(name = "wrap_up_completed_at") var wrapUpCompletedAt: Instant? = null,
    @Column(length = 5000) var metadata: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "support_notes",
    indexes = [
        Index(name = "idx_support_notes_customer_created", columnList = "customer_user_id,created_at"),
        Index(name = "idx_support_notes_case_created", columnList = "case_id,created_at")
    ]
)
class SupportNoteEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "case_id") var caseId: Long? = null,
    @Column(name = "conversation_id") var conversationId: Long? = null,
    @Column(name = "customer_user_id", nullable = false) var customerUserId: Long = 0,
    @Column(name = "author_user_id", nullable = false) var authorUserId: Long = 0,
    @Column(nullable = false, length = 20) var visibility: String = "INTERNAL",
    @Column(nullable = false, length = 2000) var note: String = "",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "support_call_requests",
    indexes = [
        Index(name = "idx_support_call_requests_status_requested", columnList = "status,requested_at"),
        Index(name = "idx_support_call_requests_customer_requested", columnList = "customer_user_id,requested_at")
    ]
)
class SupportCallRequestEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "request_id", nullable = false, unique = true, length = 40) var requestId: String = UUID.randomUUID().toString(),
    @Column(name = "customer_user_id", nullable = false) var customerUserId: Long = 0,
    @Column(name = "case_id") var caseId: Long? = null,
    @Column(name = "conversation_id") var conversationId: Long? = null,
    @Column(nullable = false, length = 30) var status: String = "PENDING",
    @Column(length = 500) var reason: String? = null,
    @Column(name = "requested_at", nullable = false) var requestedAt: Instant = Instant.now(),
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant = Instant.now(),
    @Column(name = "reviewed_by_user_id") var reviewedByUserId: Long? = null,
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null,
    @Column(name = "review_note", length = 1000) var reviewNote: String? = null,
    @Column(name = "voice_call_id", length = 40) var voiceCallId: String? = null,
    @Column(name = "assigned_user_id") var assignedUserId: Long? = null,
    @Column(name = "claimed_at") var claimedAt: Instant? = null,
    @Column(length = 60) var outcome: String? = null,
    @Column(name = "outcome_at") var outcomeAt: Instant? = null
)


@Entity
@Table(
    name = "support_messages",
    indexes = [
        Index(name = "idx_support_messages_conversation_created", columnList = "conversation_id,created_at"),
        Index(name = "idx_support_messages_customer_created", columnList = "customer_user_id,created_at")
    ]
)
class SupportMessageEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "message_id", nullable = false, unique = true, length = 40) var messageId: String = UUID.randomUUID().toString(),
    @Column(name = "conversation_id", nullable = false) var conversationId: Long = 0,
    @Column(name = "case_id") var caseId: Long? = null,
    @Column(name = "customer_user_id", nullable = false) var customerUserId: Long = 0,
    @Column(name = "sender_user_id") var senderUserId: Long? = null,
    @Column(name = "sender_type", nullable = false, length = 20) var senderType: String = "CUSTOMER",
    @Column(nullable = false, length = 4000) var message: String = "",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "customer_read_at") var customerReadAt: Instant? = null,
    @Column(name = "staff_read_at") var staffReadAt: Instant? = null
)
