package com.recharge.backend.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "support_tickets",
    indexes = [
        Index(name = "idx_support_tickets_customer_updated", columnList = "customer_user_id, updated_at"),
        Index(name = "idx_support_tickets_queue", columnList = "status, priority, updated_at"),
        Index(name = "idx_support_tickets_assignee", columnList = "assigned_agent_user_id, status, updated_at")
    ]
)
class SupportTicketEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "ticket_id", nullable = false, unique = true, length = 40) var ticketId: String = "SC-" + UUID.randomUUID().toString().substring(0, 8).uppercase(),
    @Column(name = "customer_user_id", nullable = false) var customerUserId: Long = 0,
    @Column(nullable = false, length = 40) var category: String = "OTHER",
    @Column(nullable = false, length = 20) var priority: String = "NORMAL",
    @Column(nullable = false, length = 30) var status: String = "OPEN",
    @Column(name = "assigned_agent_user_id") var assignedAgentUserId: Long? = null,
    @Column(nullable = false, length = 180) var subject: String = "",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
    @Column(name = "closed_at") var closedAt: Instant? = null,
    @Column(name = "last_customer_reply_at") var lastCustomerReplyAt: Instant? = null,
    @Column(name = "last_agent_reply_at") var lastAgentReplyAt: Instant? = null
)

@Entity
@Table(
    name = "support_ticket_messages",
    indexes = [
        Index(name = "idx_support_ticket_messages_ticket_created", columnList = "ticket_id, created_at")
    ]
)
class SupportTicketMessageEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "ticket_id", nullable = false, length = 40) var ticketId: String = "",
    @Column(name = "sender_user_id", nullable = false) var senderUserId: Long = 0,
    @Column(name = "sender_role", nullable = false, length = 30) var senderRole: String = "CLIENT",
    @Column(name = "body", nullable = false, length = 8000) var body: String = "",
    @Column(name = "internal_note", nullable = false) var internalNote: Boolean = false,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)