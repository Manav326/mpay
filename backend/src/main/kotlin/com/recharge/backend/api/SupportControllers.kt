package com.recharge.backend.api

import com.recharge.backend.repository.UserRepository
import com.recharge.backend.service.RoleAccessService
import com.recharge.backend.service.SupportService
import com.recharge.backend.service.VoiceCallService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/support")
class CustomerSupportController(
    private val users: UserRepository,
    private val support: SupportService
) {
    private fun currentUser(authentication: Authentication) =
        authentication.name.toLongOrNull()?.let {
            users.findById(it).orElseThrow { IllegalArgumentException("User not found") }
        } ?: throw IllegalStateException("Invalid authenticated user")

    @GetMapping("/overview")
    fun overview(authentication: Authentication): CustomerSupportOverviewResponse =
        support.customerOverview(currentUser(authentication))

    @PostMapping("/call-request")
    fun requestCall(
        authentication: Authentication,
        @Valid @RequestBody request: CreateSupportCallRequest
    ): SupportCallRequestResponse =
        support.requestCustomerCall(currentUser(authentication), request.reason)

    @PostMapping("/call-request/{requestId}/cancel")
    fun cancelRequest(
        authentication: Authentication,
        @PathVariable requestId: String
    ): SupportCallRequestResponse =
        support.cancelCustomerCallRequest(currentUser(authentication), requestId)
}

@RestController
@RequestMapping("/api/v1/admin/customer-care")
class CustomerCareAdminController(
    private val users: UserRepository,
    private val support: SupportService,
    private val voiceCalls: VoiceCallService,
    private val roleAccess: RoleAccessService
) {
    private fun currentUser(authentication: Authentication) =
        authentication.name.toLongOrNull()?.let {
            users.findById(it).orElseThrow { IllegalArgumentException("User not found") }
        } ?: throw IllegalStateException("Invalid authenticated user")

    @GetMapping("/requests")
    fun requests(authentication: Authentication): List<SupportCallRequestResponse> =
        support.pendingRequests(currentUser(authentication))

    @GetMapping("/requests/{requestId}")
    fun request(
        authentication: Authentication,
        @PathVariable requestId: String
    ): SupportCallRequestResponse =
        support.request(currentUser(authentication), requestId)

    @PostMapping("/requests/{requestId}/call")
    fun call(
        authentication: Authentication,
        @PathVariable requestId: String
    ): VoiceCallResponse {
        val viewer = currentUser(authentication)
        roleAccess.requirePermission(viewer, SupportService.SUPPORT_MANAGE)
        val request = support.request(viewer, requestId)
        val customerPublicId = request.customerPublicId
            ?: throw IllegalStateException("Support request customer is unavailable")
        return voiceCalls.create(viewer, customerPublicId, request.requestId)
    }

    @PostMapping("/requests/{requestId}/decline")
    fun decline(
        authentication: Authentication,
        @PathVariable requestId: String,
        @Valid @RequestBody request: SupportRequestDecisionRequest
    ): SupportCallRequestResponse =
        support.declineRequest(currentUser(authentication), requestId, request.note)

    @GetMapping("/customers/{publicId}")
    fun customer(
        authentication: Authentication,
        @PathVariable publicId: String
    ): SupportCustomerResponse =
        support.customer(currentUser(authentication), publicId)

    @GetMapping("/customers/{publicId}/callback-access")
    fun callbackAccess(
        authentication: Authentication,
        @PathVariable publicId: String
    ): CustomerCallbackAccessResponse =
        support.callbackAccess(currentUser(authentication), publicId)

    @PutMapping("/customers/{publicId}/callback-access")
    fun updateCallbackAccess(
        authentication: Authentication,
        @PathVariable publicId: String,
        @Valid @RequestBody request: CustomerCallbackAccessRequest
    ): CustomerCallbackAccessResponse =
        support.setCallbackAccess(currentUser(authentication), publicId, request.enabled)

    @PutMapping("/cases/{caseId}")
    fun updateCase(
        authentication: Authentication,
        @PathVariable caseId: String,
        @Valid @RequestBody request: UpdateSupportCaseRequest
    ): SupportCaseResponse =
        support.updateCase(currentUser(authentication), caseId, request)

    @PostMapping("/cases/{caseId}/notes")
    fun addNote(
        authentication: Authentication,
        @PathVariable caseId: String,
        @Valid @RequestBody request: CreateSupportNoteRequest
    ): SupportNoteResponse =
        support.addNote(currentUser(authentication), caseId, request)
}
