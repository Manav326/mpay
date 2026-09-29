package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.domain.SupportTicketEntity
import com.recharge.backend.domain.SupportTicketMessageEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.SupportTicketMessageRepository
import com.recharge.backend.repository.SupportTicketRepository
import com.recharge.backend.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class CustomerCareService(
    private val tickets: SupportTicketRepository,
    private val messages: SupportTicketMessageRepository,
    private val users: UserRepository,
    private val roleAccess: RoleAccessService
) {
    private val allowedCategories = setOf(
        "ACCOUNT", "WALLET", "RECHARGE", "WITHDRAWAL", "RENTAL", "PAYMENT", "VOICE_CALL", "OTHER"
    )
    private val allowedPriorities = setOf("LOW", "NORMAL", "HIGH", "URGENT")
    private val allowedStatuses = setOf("OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED")

    @Transactional
    fun create(customer: UserEntity, request: CreateSupportTicketRequest): SupportTicketResponse {
        requireClient(customer)
        val category = normalizeCategory(request.category)
        val now = Instant.now()
        val ticket = tickets.saveAndFlush(
            SupportTicketEntity(
                ticketId = nextTicketId(),
                customerUserId = requireNotNull(customer.id),
                category = category,
                priority = "NORMAL",
                status = "OPEN",
                subject = request.subject.trim(),
                createdAt = now,
                updatedAt = now,
                lastCustomerReplyAt = now
            )
        )
        messages.save(
            SupportTicketMessageEntity(
                ticketId = ticket.ticketId,
                senderUserId = requireNotNull(customer.id),
                senderRole = "CLIENT",
                body = request.message.trim(),
                internalNote = false,
                createdAt = now
            )
        )
        return ticketResponse(ticket, includeInternal = false)
    }

    fun listCustomerTickets(customer: UserEntity, page: Int, size: Int, status: String?): SupportTicketPageResponse {
        requireClient(customer)
        validatePaging(page, size)
        val normalizedStatus = status?.trim()?.uppercase()?.takeUnless { it.isBlank() || it == "ALL" }
            ?.also(::validateStatus)
        val result = tickets.findCustomerTickets(
            requireNotNull(customer.id), normalizedStatus, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        )
        return pageResponse(result, includeCustomer = false)
    }

    fun getCustomerTicket(customer: UserEntity, ticketId: String): SupportTicketResponse {
        requireClient(customer)
        val ticket = findTicket(ticketId)
        requireCustomer(ticket, customer)
        return ticketResponse(ticket, includeInternal = false)
    }

    @Transactional
    fun replyAsCustomer(customer: UserEntity, ticketId: String, request: CreateSupportMessageRequest): SupportTicketResponse {
        requireClient(customer)
        val ticket = findTicket(ticketId)
        requireCustomer(ticket, customer)
        if (ticket.status == "CLOSED") {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This support case is closed. Please create a new case.")
        }

        val now = Instant.now()
        if (ticket.status == "RESOLVED") {
            ticket.status = "OPEN"
            ticket.resolvedAt = null
        } else if (ticket.status == "WAITING_FOR_CUSTOMER") {
            ticket.status = "IN_PROGRESS"
        }
        ticket.updatedAt = now
        ticket.lastCustomerReplyAt = now
        tickets.save(ticket)
        messages.save(
            SupportTicketMessageEntity(
                ticketId = ticket.ticketId,
                senderUserId = requireNotNull(customer.id),
                senderRole = "CLIENT",
                body = request.message.trim(),
                internalNote = false,
                createdAt = now
            )
        )
        return ticketResponse(ticket, includeInternal = false)
    }

    @Transactional
    fun closeAsCustomer(customer: UserEntity, ticketId: String): SupportTicketResponse {
        requireClient(customer)
        val ticket = findTicket(ticketId)
        requireCustomer(ticket, customer)
        if (ticket.status == "CLOSED") return ticketResponse(ticket, includeInternal = false)

        val now = Instant.now()
        ticket.status = "CLOSED"
        ticket.closedAt = now
        ticket.updatedAt = now
        tickets.save(ticket)
        return ticketResponse(ticket, includeInternal = false)
    }

    fun searchAdmin(
        viewer: UserEntity,
        page: Int,
        size: Int,
        status: String?,
        priority: String?,
        category: String?,
        assignment: String?,
        query: String?
    ): SupportTicketPageResponse {
        roleAccess.requirePermission(viewer, "VIEW_CUSTOMER_CARE")
        validatePaging(page, size)
        val normalizedStatus = status?.trim()?.uppercase()?.takeUnless { it.isBlank() || it == "ALL" }?.also(::validateStatus)
        val normalizedPriority = priority?.trim()?.uppercase()?.takeUnless { it.isBlank() || it == "ALL" }?.also(::validatePriority)
        val normalizedCategory = category?.trim()?.uppercase()?.takeUnless { it.isBlank() || it == "ALL" }?.also(::validateCategory)
        val normalizedAssignment = assignment?.trim()?.uppercase().takeUnless { it.isNullOrBlank() } ?: "ALL"
        if (normalizedAssignment !in setOf("ALL", "MINE", "UNASSIGNED")) throw IllegalArgumentException("Invalid assignment filter")
        val normalizedQuery = query?.trim()?.takeIf { it.isNotBlank() }?.take(120)
        val result = tickets.searchAdmin(
            normalizedStatus,
            normalizedPriority,
            normalizedCategory,
            normalizedAssignment,
            requireNotNull(viewer.id),
            normalizedQuery,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        )
        return pageResponse(result, includeCustomer = true)
    }

    fun getAdminTicket(viewer: UserEntity, ticketId: String): SupportTicketResponse {
        roleAccess.requirePermission(viewer, "VIEW_CUSTOMER_CARE")
        return ticketResponse(findTicket(ticketId), includeInternal = true)
    }

    fun queueSummary(viewer: UserEntity): SupportQueueSummaryResponse {
        roleAccess.requirePermission(viewer, "VIEW_CUSTOMER_CARE")
        val activeStatuses = setOf("OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER")
        return SupportQueueSummaryResponse(
            open = tickets.countByStatus("OPEN"),
            inProgress = tickets.countByStatus("IN_PROGRESS"),
            waitingForCustomer = tickets.countByStatus("WAITING_FOR_CUSTOMER"),
            resolved = tickets.countByStatus("RESOLVED"),
            urgent = tickets.countByPriorityAndStatusIn("URGENT", activeStatuses),
            mine = tickets.countByAssignedAgentUserIdAndStatusIn(requireNotNull(viewer.id), activeStatuses),
            unassigned = tickets.countByAssignedAgentUserIdIsNullAndStatusIn(activeStatuses)
        )
    }

    fun agents(viewer: UserEntity): List<SupportAgentResponse> {
        roleAccess.requirePermission(viewer, "VIEW_CUSTOMER_CARE")
        val portalRoles = roleAccess.portalRoles()
        if (portalRoles.isEmpty()) return emptyList()
        val visibleRoles = roleAccess.visibleRolesFor(viewer.role)
            .filter { it in portalRoles }
            .toSet()
        if (visibleRoles.isEmpty()) return emptyList()
        return users.findAllByRoleInOrderByCreatedAtDesc(visibleRoles.toList())
            .filter { it.active && it.deletedAt == null }
            .map { SupportAgentResponse(it.publicId, it.name, it.role.uppercase()) }
    }

    @Transactional
    fun replyAsAgent(viewer: UserEntity, ticketId: String, request: CreateSupportMessageRequest, internalNote: Boolean): SupportTicketResponse {
        roleAccess.requirePermission(viewer, "MANAGE_CUSTOMER_CARE")
        val ticket = findTicket(ticketId)
        if (ticket.status == "CLOSED") {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This support case is closed.")
        }

        val now = Instant.now()
        if (!internalNote) {
            ticket.status = "WAITING_FOR_CUSTOMER"
            ticket.lastAgentReplyAt = now
        }
        ticket.updatedAt = now
        tickets.save(ticket)
        messages.save(
            SupportTicketMessageEntity(
                ticketId = ticket.ticketId,
                senderUserId = requireNotNull(viewer.id),
                senderRole = viewer.role.uppercase(),
                body = request.message.trim(),
                internalNote = internalNote,
                createdAt = now
            )
        )
        return ticketResponse(ticket, includeInternal = true)
    }

    @Transactional
    fun updateStatus(viewer: UserEntity, ticketId: String, request: UpdateSupportStatusRequest): SupportTicketResponse {
        roleAccess.requirePermission(viewer, "MANAGE_CUSTOMER_CARE")
        val ticket = findTicket(ticketId)
        val next = request.status.trim().uppercase()
        validateStatus(next)
        if (ticket.status == "CLOSED" && next != "CLOSED") {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Closed support cases cannot be reopened.")
        }
        if (next == "CLOSED") {
            ticket.closedAt = ticket.closedAt ?: Instant.now()
            ticket.resolvedAt = ticket.resolvedAt ?: if (ticket.status == "RESOLVED") ticket.updatedAt else null
        } else if (ticket.status == "CLOSED") {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Closed support cases cannot be reopened.")
        }
        if (next == "RESOLVED") ticket.resolvedAt = ticket.resolvedAt ?: Instant.now()
        if (next != "RESOLVED") ticket.resolvedAt = null
        if (next != "CLOSED") ticket.closedAt = null
        ticket.status = next
        ticket.updatedAt = Instant.now()
        tickets.save(ticket)
        return ticketResponse(ticket, includeInternal = true)
    }

    @Transactional
    fun updatePriority(viewer: UserEntity, ticketId: String, request: UpdateSupportPriorityRequest): SupportTicketResponse {
        roleAccess.requirePermission(viewer, "MANAGE_CUSTOMER_CARE")
        val ticket = findTicket(ticketId)
        val next = request.priority.trim().uppercase()
        validatePriority(next)
        ticket.priority = next
        ticket.updatedAt = Instant.now()
        tickets.save(ticket)
        return ticketResponse(ticket, includeInternal = true)
    }

    @Transactional
    fun updateAssignment(viewer: UserEntity, ticketId: String, request: UpdateSupportAssignmentRequest): SupportTicketResponse {
        roleAccess.requirePermission(viewer, "MANAGE_CUSTOMER_CARE")
        val ticket = findTicket(ticketId)
        val agentPublicId = request.agentPublicId?.trim()?.takeIf { it.isNotBlank() }
        if (agentPublicId == null) {
            ticket.assignedAgentUserId = null
        } else {
            val agent = users.findByPublicId(agentPublicId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Support agent not found")
            }
            val portalRoles = roleAccess.portalRoles()
            if (agent.role.uppercase() !in portalRoles || !agent.active || agent.deletedAt != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Selected support agent is not available")
            }
            if (!roleAccess.canView(viewer, agent)) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot assign cases to this support agent")
            }
            ticket.assignedAgentUserId = requireNotNull(agent.id)
        }
        ticket.updatedAt = Instant.now()
        tickets.save(ticket)
        return ticketResponse(ticket, includeInternal = true)
    }

    private fun pageResponse(page: org.springframework.data.domain.Page<SupportTicketEntity>, includeCustomer: Boolean): SupportTicketPageResponse =
        SupportTicketPageResponse(
            items = page.content.map { summaryResponse(it, includeCustomer) },
            page = page.number,
            size = page.size,
            totalItems = page.totalElements,
            totalPages = page.totalPages,
            hasNext = page.hasNext()
        )

    private fun summaryResponse(ticket: SupportTicketEntity, includeCustomer: Boolean): SupportTicketSummaryResponse {
        val customer = if (includeCustomer) users.findById(ticket.customerUserId).orElse(null) else null
        val agent = ticket.assignedAgentUserId?.let { users.findById(it).orElse(null) }
        return SupportTicketSummaryResponse(
            ticketId = ticket.ticketId,
            subject = ticket.subject,
            category = ticket.category,
            priority = ticket.priority,
            status = ticket.status,
            assignedAgentPublicId = agent?.publicId,
            assignedAgentName = agent?.name,
            customerPublicId = customer?.publicId,
            customerName = customer?.name,
            customerMobile = customer?.mobile,
            updatedAt = ticket.updatedAt,
            createdAt = ticket.createdAt,
            lastCustomerReplyAt = ticket.lastCustomerReplyAt,
            lastAgentReplyAt = ticket.lastAgentReplyAt
        )
    }

    private fun ticketResponse(ticket: SupportTicketEntity, includeInternal: Boolean): SupportTicketResponse {
        val customer = users.findById(ticket.customerUserId).orElse(null)
        val agent = ticket.assignedAgentUserId?.let { users.findById(it).orElse(null) }
        val renderedMessages = messages.findByTicketIdOrderByCreatedAtAsc(ticket.ticketId)
            .filter { includeInternal || !it.internalNote }
            .takeLast(250)
            .map { message ->
                val author = users.findById(message.senderUserId).orElse(null)
                SupportTicketMessageResponse(
                    id = requireNotNull(message.id),
                    authorName = author?.name,
                    authorRole = message.senderRole,
                    body = message.body,
                    internalNote = message.internalNote,
                    createdAt = message.createdAt
                )
            }
        return SupportTicketResponse(
            ticketId = ticket.ticketId,
            subject = ticket.subject,
            category = ticket.category,
            priority = ticket.priority,
            status = ticket.status,
            assignedAgentPublicId = agent?.publicId,
            assignedAgentName = agent?.name,
            customerPublicId = customer?.publicId,
            customerName = customer?.name,
            customerMobile = customer?.mobile,
            createdAt = ticket.createdAt,
            updatedAt = ticket.updatedAt,
            resolvedAt = ticket.resolvedAt,
            closedAt = ticket.closedAt,
            messages = renderedMessages
        )
    }

    private fun findTicket(ticketId: String): SupportTicketEntity =
        tickets.findByTicketId(ticketId.trim()).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Support case not found")
        }

    private fun requireCustomer(ticket: SupportTicketEntity, customer: UserEntity) {
        if (ticket.customerUserId != requireNotNull(customer.id)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this support case")
        }
    }

    private fun requireClient(user: UserEntity) {
        if (!user.role.equals("CLIENT", true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Customer support is available for customer accounts")
        }
    }

    private fun validatePaging(page: Int, size: Int) {
        require(page >= 0) { "Page must be non-negative" }
        require(size in 1..50) { "Page size must be between 1 and 50" }
    }

    private fun normalizeCategory(value: String): String {
        val normalized = value.trim().uppercase()
        validateCategory(normalized)
        return normalized
    }

    private fun validateCategory(value: String) {
        if (value !in allowedCategories) throw IllegalArgumentException("Unsupported support category")
    }

    private fun validatePriority(value: String) {
        if (value !in allowedPriorities) throw IllegalArgumentException("Unsupported support priority")
    }

    private fun validateStatus(value: String) {
        if (value !in allowedStatuses) throw IllegalArgumentException("Unsupported support status")
    }

    private fun nextTicketId(): String {
        repeat(5) {
            val id = "SC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).uppercase()
            if (!tickets.findByTicketId(id).isPresent) return id
        }
        throw IllegalStateException("Unable to allocate support case id")
    }
}