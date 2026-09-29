package com.recharge.client.core.model

data class CreateSupportTicketRequest(
    val category: String,
    val subject: String,
    val message: String
)

data class SupportMessageRequest(
    val message: String
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
    val updatedAt: String,
    val createdAt: String,
    val lastCustomerReplyAt: String? = null,
    val lastAgentReplyAt: String? = null
)

data class SupportTicketMessageResponse(
    val id: Long,
    val authorName: String? = null,
    val authorRole: String,
    val body: String,
    val internalNote: Boolean = false,
    val createdAt: String
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
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String? = null,
    val closedAt: String? = null,
    val messages: List<SupportTicketMessageResponse> = emptyList()
)

data class SupportTicketPageResponse(
    val items: List<SupportTicketSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasNext: Boolean
)
