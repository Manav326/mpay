package com.recharge.backend.api

import com.recharge.backend.repository.UserRepository
import com.recharge.backend.service.CustomerCareService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/support")
class CustomerSupportController(
    private val users: UserRepository,
    private val support: CustomerCareService
) {
    private fun currentUser(authentication: Authentication) =
        authentication.name.toLongOrNull()?.let { users.findById(it).orElseThrow { IllegalArgumentException("User not found") } }
            ?: throw IllegalStateException("Invalid authenticated user")

    @PostMapping("/tickets")
    fun create(
        authentication: Authentication,
        @RequestBody request: CreateSupportTicketRequest
    ): SupportTicketResponse = support.create(currentUser(authentication), request)

    @GetMapping("/tickets")
    fun list(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: String?
    ): SupportTicketPageResponse = support.listCustomerTickets(currentUser(authentication), page, size, status)

    @GetMapping("/tickets/{ticketId}")
    fun get(authentication: Authentication, @PathVariable ticketId: String): SupportTicketResponse =
        support.getCustomerTicket(currentUser(authentication), ticketId)

    @PostMapping("/tickets/{ticketId}/messages")
    fun reply(
        authentication: Authentication,
        @PathVariable ticketId: String,
        @RequestBody request: CreateSupportMessageRequest
    ): SupportTicketResponse = support.replyAsCustomer(currentUser(authentication), ticketId, request)

    @PostMapping("/tickets/{ticketId}/close")
    fun close(authentication: Authentication, @PathVariable ticketId: String): SupportTicketResponse =
        support.closeAsCustomer(currentUser(authentication), ticketId)
}

@RestController
@RequestMapping("/api/v1/admin/customer-care")
class CustomerCareAdminController(
    private val users: UserRepository,
    private val support: CustomerCareService
) {
    private fun currentUser(authentication: Authentication) =
        authentication.name.toLongOrNull()?.let { users.findById(it).orElseThrow { IllegalArgumentException("User not found") } }
            ?: throw IllegalStateException("Invalid authenticated user")

    @GetMapping("/tickets")
    fun tickets(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) priority: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) assignment: String?,
        @RequestParam(required = false) q: String?
    ): SupportTicketPageResponse = support.searchAdmin(currentUser(authentication), page, size, status, priority, category, assignment, q)

    @GetMapping("/tickets/{ticketId}")
    fun get(authentication: Authentication, @PathVariable ticketId: String): SupportTicketResponse =
        support.getAdminTicket(currentUser(authentication), ticketId)

    @GetMapping("/summary")
    fun summary(authentication: Authentication): SupportQueueSummaryResponse =
        support.queueSummary(currentUser(authentication))

    @GetMapping("/agents")
    fun agents(authentication: Authentication): List<SupportAgentResponse> =
        support.agents(currentUser(authentication))

    @PostMapping("/tickets/{ticketId}/messages")
    fun reply(
        authentication: Authentication,
        @PathVariable ticketId: String,
        @RequestBody request: CreateSupportMessageRequest
    ): SupportTicketResponse = support.replyAsAgent(currentUser(authentication), ticketId, request, internalNote = false)

    @PostMapping("/tickets/{ticketId}/notes")
    fun note(
        authentication: Authentication,
        @PathVariable ticketId: String,
        @RequestBody request: CreateSupportMessageRequest
    ): SupportTicketResponse = support.replyAsAgent(currentUser(authentication), ticketId, request, internalNote = true)

    @PutMapping("/tickets/{ticketId}/status")
    fun status(
        authentication: Authentication,
        @PathVariable ticketId: String,
        @RequestBody request: UpdateSupportStatusRequest
    ): SupportTicketResponse = support.updateStatus(currentUser(authentication), ticketId, request)

    @PutMapping("/tickets/{ticketId}/priority")
    fun priority(
        authentication: Authentication,
        @PathVariable ticketId: String,
        @RequestBody request: UpdateSupportPriorityRequest
    ): SupportTicketResponse = support.updatePriority(currentUser(authentication), ticketId, request)

    @PutMapping("/tickets/{ticketId}/assignment")
    fun assignment(
        authentication: Authentication,
        @PathVariable ticketId: String,
        @RequestBody request: UpdateSupportAssignmentRequest
    ): SupportTicketResponse = support.updateAssignment(currentUser(authentication), ticketId, request)
}