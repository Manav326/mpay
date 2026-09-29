package com.recharge.backend.api

import java.time.Instant

data class SupportCallRequestResponse(
    val requestId: String,
    val status: String,
    val reason: String?,
    val requestedAt: String,
    val expiresAt: String,
    val caseId: String?,
    val customerPublicId: String? = null,
    val customerName: String? = null,
    val customerMobile: String? = null,
    val voiceCallId: String? = null,
    val assignedUserPublicId: String? = null,
    val assignedUserName: String? = null,
    val claimedAt: String? = null,
    val outcome: String? = null
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
    val caseId: String?,
    val channel: String,
    val direction: String,
    val status: String,
    val startedAt: String,
    val endedAt: String?,
    val durationSeconds: Long?,
    val durationLabel: String?,
    val ringDurationSeconds: Long? = null,
    val ringDurationLabel: String? = null,
    val handlingDurationSeconds: Long? = null,
    val handlingDurationLabel: String? = null,
    val wrapUpCompletedAt: String? = null,
    val wrapUpDurationSeconds: Long? = null,
    val wrapUpDurationLabel: String? = null,
    val outcome: String?,
    val voiceCallId: String?,
    val actorUserPublicId: String?,
    val actorName: String?
)

data class SupportNoteResponse(
    val id: Long,
    val caseId: String?,
    val visibility: String,
    val note: String,
    val authorUserPublicId: String?,
    val authorName: String?,
    val createdAt: String
)

data class SupportCaseEventResponse(
    val eventId: String,
    val caseId: String,
    val eventType: String,
    val visibility: String,
    val channel: String?,
    val summary: String,
    val actorUserPublicId: String?,
    val actorName: String?,
    val createdAt: String
)

data class SupportCustomerResponse(
    val customerPublicId: String,
    val customerName: String?,
    val mobile: String,
    val callbackRequestEnabled: Boolean,
    val pendingRequest: SupportCallRequestResponse?,
    val openCases: List<SupportCaseResponse>,
    val interactions: List<SupportInteractionResponse>,
    val notes: List<SupportNoteResponse>,
    val events: List<SupportCaseEventResponse> = emptyList()
)

data class CustomerSupportOverviewResponse(
    val callbackRequestEnabled: Boolean,
    val pendingRequest: SupportCallRequestResponse?,
    val cases: List<SupportCaseResponse>,
    val interactions: List<SupportInteractionResponse>,
    val customerNotes: List<SupportNoteResponse>,
    val events: List<SupportCaseEventResponse> = emptyList()
)

data class CreateSupportCallRequest(
    @field:jakarta.validation.constraints.Size(max = 500)
    val reason: String? = null
)

data class UpdateSupportCaseRequest(
    @field:jakarta.validation.constraints.NotBlank
    val status: String,
    @field:jakarta.validation.constraints.Size(max = 100)
    val resolutionCode: String? = null,
    @field:jakarta.validation.constraints.Size(max = 1200)
    val resolutionNote: String? = null
)

data class CreateSupportNoteRequest(
    @field:jakarta.validation.constraints.NotBlank
    @field:jakarta.validation.constraints.Size(max = 2000)
    val note: String,
    val visibility: String = "INTERNAL"
)

data class SupportRequestDecisionResponse(
    val request: SupportCallRequestResponse,
    val message: String
)

data class CustomerCallbackAccessResponse(
    val publicUserId: String,
    val enabled: Boolean
)

data class CustomerCallbackAccessRequest(
    val enabled: Boolean
)

data class VoiceCallSupportRequest(
    val supportRequestId: String? = null
)


data class SupportRequestDecisionRequest(
    @field:jakarta.validation.constraints.Size(max = 1000)
    val note: String? = null
)
