package com.recharge.backend.repository

import com.recharge.backend.domain.CallPushDeviceEntity
import com.recharge.backend.domain.UserPermissionOverrideEntity
import com.recharge.backend.domain.VoiceCallEntity
import com.recharge.backend.domain.VoiceCallParticipantEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface VoiceCallRepository : JpaRepository<VoiceCallEntity, Long> {
    fun findByCallId(callId: String): Optional<VoiceCallEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VoiceCallEntity c where c.callId = :callId")
    fun findByCallIdForUpdate(@Param("callId") callId: String): Optional<VoiceCallEntity>

    fun findAllByStatus(status: String): List<VoiceCallEntity>

    fun findAllByStatusAndRingingExpiresAtBefore(status: String, before: Instant): List<VoiceCallEntity>
    fun findAllByStatusAndAcceptedAtBefore(status: String, before: Instant): List<VoiceCallEntity>
}

interface VoiceCallParticipantRepository : JpaRepository<VoiceCallParticipantEntity, Long> {
    fun existsByAccountTypeAndAccountId(accountType: String, accountId: Long): Boolean
    fun findAllByCallId(callId: String): List<VoiceCallParticipantEntity>
    fun deleteAllByCallId(callId: String)

    @Query("select p from VoiceCallParticipantEntity p where p.accountType = :accountType and p.accountId = :accountId")
    fun findByAccountTypeAndAccountId(@Param("accountType") accountType: String, @Param("accountId") accountId: Long): Optional<VoiceCallParticipantEntity>
}

interface CallPushDeviceRepository : JpaRepository<CallPushDeviceEntity, Long> {
    fun findAllByUserIdAndActiveTrue(userId: Long): List<CallPushDeviceEntity>
    fun findByToken(token: String): Optional<CallPushDeviceEntity>
}

interface UserPermissionOverrideRepository : JpaRepository<UserPermissionOverrideEntity, Long> {
    fun findAllByUserId(userId: Long): List<UserPermissionOverrideEntity>
    fun findByUserIdAndPermissionIgnoreCase(userId: Long, permission: String): UserPermissionOverrideEntity?
}
