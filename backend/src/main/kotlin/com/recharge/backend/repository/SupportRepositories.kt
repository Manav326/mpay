package com.recharge.backend.repository

import com.recharge.backend.domain.SupportTicketEntity
import com.recharge.backend.domain.SupportTicketMessageEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface SupportTicketRepository : JpaRepository<SupportTicketEntity, Long> {
    fun findByTicketId(ticketId: String): Optional<SupportTicketEntity>

    @Query("""
        select t from SupportTicketEntity t
        where t.customerUserId = :customerUserId
          and (:status is null or t.status = :status)
        order by t.updatedAt desc
    """)
    fun findCustomerTickets(
        @Param("customerUserId") customerUserId: Long,
        @Param("status") status: String?,
        pageable: Pageable
    ): Page<SupportTicketEntity>

    @Query("""
        select t from SupportTicketEntity t
        where (:status is null or t.status = :status)
          and (:priority is null or t.priority = :priority)
          and (:category is null or t.category = :category)
          and (
              :assignment = 'ALL'
              or (:assignment = 'UNASSIGNED' and t.assignedAgentUserId is null)
              or (:assignment = 'MINE' and t.assignedAgentUserId = :agentUserId)
          )
          and (
              :query is null
              or lower(t.ticketId) like lower(concat('%', :query, '%'))
              or lower(t.subject) like lower(concat('%', :query, '%'))
          )
        order by t.updatedAt desc
    """)
    fun searchAdminTickets(
        @Param("status") status: String?,
        @Param("priority") priority: String?,
        @Param("category") category: String?,
        @Param("assignment") assignment: String,
        @Param("agentUserId") agentUserId: Long?,
        @Param("query") query: String?,
        pageable: Pageable
    ): Page<SupportTicketEntity>

    fun countByStatus(status: String): Long
    fun countByAssignedAgentUserIdAndStatusIn(agentUserId: Long, statuses: Collection<String>): Long
    fun countByAssignedAgentUserIdIsNullAndStatusIn(statuses: Collection<String>): Long
    fun countByPriorityAndStatusIn(priority: String, statuses: Collection<String>): Long
}

interface SupportTicketMessageRepository : JpaRepository<SupportTicketMessageEntity, Long> {
    fun findByTicketIdOrderByCreatedAtAsc(ticketId: String): List<SupportTicketMessageEntity>
}