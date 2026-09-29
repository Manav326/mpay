package com.recharge.backend.repository

import com.recharge.backend.domain.SupportCallRequestEntity
import com.recharge.backend.domain.SupportCaseEventEntity
import com.recharge.backend.domain.SupportCaseEntity
import com.recharge.backend.domain.SupportConversationEntity
import com.recharge.backend.domain.SupportInteractionEntity
import com.recharge.backend.domain.SupportNoteEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface SupportCaseRepository : JpaRepository<SupportCaseEntity, Long> {
    fun findByCaseId(caseId: String): Optional<SupportCaseEntity>
    fun findAllByCustomerUserIdOrderByUpdatedAtDesc(customerUserId: Long): List<SupportCaseEntity>
    fun findFirstByCustomerUserIdAndStatusInOrderByUpdatedAtDesc(customerUserId: Long, statuses: Collection<String>): Optional<SupportCaseEntity>
}

interface SupportConversationRepository : JpaRepository<SupportConversationEntity, Long> {
    fun findByConversationId(conversationId: String): Optional<SupportConversationEntity>
    fun findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(customerUserId: Long, status: String): Optional<SupportConversationEntity>
    fun findAllByCustomerUserIdOrderByLastActivityAtDesc(customerUserId: Long): List<SupportConversationEntity>
}

interface SupportInteractionRepository : JpaRepository<SupportInteractionEntity, Long> {
    fun findByVoiceCallId(voiceCallId: String): Optional<SupportInteractionEntity>
    fun findAllByCustomerUserIdOrderByStartedAtDesc(customerUserId: Long): List<SupportInteractionEntity>
    fun findAllByConversationIdOrderByStartedAtDesc(conversationId: Long): List<SupportInteractionEntity>
    fun findFirstByCaseIdAndChannelOrderByStartedAtDesc(caseId: Long, channel: String): Optional<SupportInteractionEntity>
}

interface SupportNoteRepository : JpaRepository<SupportNoteEntity, Long> {
    fun findAllByCustomerUserIdOrderByCreatedAtDesc(customerUserId: Long): List<SupportNoteEntity>
    fun findAllByCaseIdOrderByCreatedAtAsc(caseId: Long): List<SupportNoteEntity>
    fun findAllByCustomerUserIdAndVisibilityOrderByCreatedAtDesc(customerUserId: Long, visibility: String): List<SupportNoteEntity>
}

interface SupportCallRequestRepository : JpaRepository<SupportCallRequestEntity, Long> {
    fun findByRequestId(requestId: String): Optional<SupportCallRequestEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SupportCallRequestEntity r where r.requestId = :requestId")
    fun findByRequestIdForUpdate(@Param("requestId") requestId: String): Optional<SupportCallRequestEntity>
    fun findFirstByCustomerUserIdAndStatusOrderByRequestedAtDesc(customerUserId: Long, status: String): Optional<SupportCallRequestEntity>
    fun findAllByStatusOrderByRequestedAtAsc(status: String): List<SupportCallRequestEntity>
    fun findAllByCustomerUserIdOrderByRequestedAtDesc(customerUserId: Long): List<SupportCallRequestEntity>
    fun findAllByStatusAndExpiresAtBefore(status: String, before: Instant): List<SupportCallRequestEntity>
}

interface SupportCaseEventRepository : JpaRepository<SupportCaseEventEntity, Long> {
    fun findAllByCustomerUserIdAndVisibilityOrderByCreatedAtDesc(customerUserId: Long, visibility: String): List<SupportCaseEventEntity>
    fun findAllByCustomerUserIdOrderByCreatedAtDesc(customerUserId: Long): List<SupportCaseEventEntity>
    fun findAllByCaseIdOrderByCreatedAtDesc(caseId: Long): List<SupportCaseEventEntity>
}
