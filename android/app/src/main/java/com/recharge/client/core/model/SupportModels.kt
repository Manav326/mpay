package com.recharge.client.core.model

data class SupportCallRequestResponse(
    val requestId: String,
    val status: String,
    val reason: String? = null,
    val requestedAt: String,
    val expiresAt: String,
    val caseId: String? = null,
    val customerPublicId: String? = null,
    val customerName: String? = null,
    val customerMobile: String? = null,
    val voiceCallId: String? = null
)

data class SupportCaseResponse(
    val caseId: String,
    val customerPublicId: String,
    val subject: String,
    val category: String,
    val priority: String,
    val status: String,
    val source: String,
    val assignedUserPublicId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String? = null,
    val resolutionCode: String? = null,
    val resolutionNote: String? = null
)

data class SupportInteractionResponse(
    val interactionId: String,
    val caseId: String? = null,
    val channel: String,
    val direction: String,
    val status: String,
    val startedAt: String,
    val endedAt: String? = null,
    val durationSeconds: Long? = null,
    val durationLabel: String? = null,
    val outcome: String? = null,
    val voiceCallId: String? = null,
    val actorUserPublicId: String? = null,
    val actorName: String? = null
)

data class SupportNoteResponse(
    val id: Long,
    val caseId: String? = null,
    val visibility: String,
    val note: String,
    val authorUserPublicId: String? = null,
    val authorName: String? = null,
    val createdAt: String
)

data class CustomerSupportOverviewResponse(
    val callbackRequestEnabled: Boolean,
    val pendingRequest: SupportCallRequestResponse? = null,
    val cases: List<SupportCaseResponse> = emptyList(),
    val interactions: List<SupportInteractionResponse> = emptyList(),
    val customerNotes: List<SupportNoteResponse> = emptyList()
)

data class CreateSupportCallRequest(
    val reason: String? = null
)
