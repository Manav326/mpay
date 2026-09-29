package com.recharge.backend.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateSupportTicketRequest(
    @field:NotBlank @field:Size(max = 40) val category: String,
    @field:NotBlank @field:Size(max = 180) val subject: String,
    @field:NotBlank @field:Size(max = 8000) val message: String
)

data class CreateSupportMessageRequest(
    @field:NotBlank @field:Size(max = 8000) val message: String
)

data class UpdateSupportStatusRequest(
    @field:NotBlank val status: String
)

data class UpdateSupportPriorityRequest(
    @field:NotBlank val priority: String
)

data class UpdateSupportAssignmentRequest(
    val agentPublicId: String? = null
)

data class SupportTicketSummaryResponse(
    val ticketId: String,
    val subject: String,
    val category: String,
    val priority: String,
    val status: String,
    val assignedAgentPublicId: String? = null,
    val assignedAgentName: String? = null,
    val customerPublicId: String? = null,
    val customerName: String? = null,
    val customerMobile: String? = null,
    val updatedAt: Instant,
    val createdAt: Instant,
    val lastCustomerReplyAt: Instant? = null,
    val lastAgentReplyAt: Instant? = null
)

data class SupportTicketMessageResponse(
    val id: Long,
    val authorName: String?,
    val authorRole: String,
    val body: String,
    val internalNote: Boolean,
    val createdAt: Instant
)

data class SupportTicketResponse(
    val ticketId: String,
    val subject: String,
    val category: String,
    val priority: String,
    val status: String,
    val assignedAgentPublicId: String? = null,
    val assignedAgentName: String? = null,
    val customerPublicId: String? = null,
    val customerName: String? = null,
    val customerMobile: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant? = null,
    val closedAt: Instant? = null,
    val messages: List<SupportTicketMessageResponse>
)

data class SupportTicketPageResponse(
    val items: List<SupportTicketSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class SupportAgentResponse(
    val publicUserId: String,
    val name: String?,
    val role: String
)

data class SupportQueueSummaryResponse(
    val open: Long,
    val inProgress: Long,
    val waitingForCustomer: Long,
    val resolved: Long,
    val urgent: Long,
    val mine: Long,
    val unassigned: Long
)