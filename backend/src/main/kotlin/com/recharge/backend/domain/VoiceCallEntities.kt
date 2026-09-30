package com.recharge.backend.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "voice_calls",
    indexes = [
        Index(name = "idx_voice_calls_status_expires", columnList = "status,ringing_expires_at"),
        Index(name = "idx_voice_calls_caller_created", columnList = "caller_user_id,created_at"),
        Index(name = "idx_voice_calls_callee_created", columnList = "callee_user_id,created_at")
    ]
)
class VoiceCallEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "call_id", nullable = false, unique = true, length = 40) var callId: String = UUID.randomUUID().toString(),
    @Column(name = "caller_user_id", nullable = false) var callerEmployeeId: Long = 0,
    @Column(name = "callee_user_id", nullable = false) var calleeUserId: Long = 0,
    @Column(nullable = false, length = 20) var status: String = "RINGING",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "ringing_expires_at", nullable = false) var ringingExpiresAt: Instant = Instant.now(),
    @Column(name = "accepted_at") var acceptedAt: Instant? = null,
    @Column(name = "connected_at") var connectedAt: Instant? = null,
    @Column(name = "ended_at") var endedAt: Instant? = null,
    @Column(name = "ended_by_user_id") var endedByAccountId: Long? = null,
    @Column(name = "ended_reason", length = 80) var endedReason: String? = null
)

@Entity
@Table(
    name = "voice_call_participants",
    indexes = [Index(name = "idx_voice_call_participants_call", columnList = "call_id")],
    uniqueConstraints = [UniqueConstraint(name = "uq_voice_call_participant_user", columnNames = ["user_id"])]
)
class VoiceCallParticipantEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "call_id", nullable = false, length = 40) var callId: String = "",
    @Column(name = "user_id", nullable = false) var accountId: Long = 0
)

@Entity
@Table(
    name = "call_push_devices",
    indexes = [Index(name = "idx_call_push_devices_user_active", columnList = "user_id,active")]
)
class CallPushDeviceEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "user_id", nullable = false) var userId: Long = 0,
    @Column(nullable = false, unique = true, length = 2048) var token: String = "",
    @Column(nullable = false, length = 20) var platform: String = "ANDROID",
    @Column(nullable = false) var active: Boolean = true,
    @Column(name = "last_seen_at", nullable = false) var lastSeenAt: Instant = Instant.now(),
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "user_permission_overrides",
    uniqueConstraints = [UniqueConstraint(name = "uq_user_permission_override", columnNames = ["user_id", "permission"])]
)
class UserPermissionOverrideEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "user_id", nullable = false) var userId: Long = 0,
    @Column(nullable = false, length = 80) var permission: String = "",
    @Column(nullable = false) var allowed: Boolean = true,
    @Column(name = "granted_by_user_id", nullable = false) var grantedByUserId: Long = 0,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)
