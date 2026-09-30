package com.recharge.backend.api

import com.recharge.backend.repository.UserRepository
import com.recharge.backend.service.AdminService
import com.recharge.backend.service.RoleAccessService
import com.recharge.backend.service.SupportAiSettingsService
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

    @GetMapping("/chat")
    fun chat(authentication: Authentication): SupportChatResponse =
        support.customerChat(currentUser(authentication))

    @PostMapping("/chat/messages")
    fun sendChatMessage(
        authentication: Authentication,
        @Valid @RequestBody request: CreateSupportMessageRequest
    ): SupportMessageResponse =
        support.sendCustomerChatMessage(currentUser(authentication), request.message)

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
    private val adminService: AdminService,
    private val support: SupportService,
    private val supportAiSettings: SupportAiSettingsService,
    private val supportAccess: com.recharge.backend.service.SupportAccessService,
    private val voiceCalls: VoiceCallService,
    private val roleAccess: RoleAccessService
) {
    private fun currentUser(authentication: Authentication) =
        authentication.name.toLongOrNull()?.let {
            users.findById(it).orElseThrow { IllegalArgumentException("User not found") }
        } ?: throw IllegalStateException("Invalid authenticated user")

    @GetMapping("/access")
    fun access(authentication: Authentication): SupportAccessResponse =
        supportAccess.access(currentUser(authentication))

    @PutMapping("/access/roles/{role}/{permission}")
    fun updateRoleAccess(
        authentication: Authentication,
        @PathVariable role: String,
        @PathVariable permission: String,
        @RequestBody request: Map<String, Boolean>
    ): SupportRoleAccessResponse =
        supportAccess.setRolePermission(currentUser(authentication), role, permission, request["enabled"] ?: false)

    @PutMapping("/access/users/{publicId}/{permission}")
    fun updateUserAccess(
        authentication: Authentication,
        @PathVariable publicId: String,
        @PathVariable permission: String,
        @RequestBody request: Map<String, String>
    ): SupportUserAccessResponse =
        supportAccess.setUserPermission(currentUser(authentication), publicId, permission, request["mode"] ?: "DEFAULT")

    @GetMapping("/ai")
    fun aiSettings(authentication: Authentication): SupportAiSettingsResponse {
        roleAccess.requirePermission(currentUser(authentication), SupportService.SUPPORT_VIEW)
        return supportAiSettings.current()
    }

    @PutMapping("/ai")
    fun updateAiSettings(
        authentication: Authentication,
        @Valid @RequestBody request: UpdateSupportAiSettingsRequest
    ): SupportAiSettingsResponse =
        supportAiSettings.update(currentUser(authentication), request.enabled)

    @GetMapping("/customers/search")
    fun searchCustomers(
        authentication: Authentication,
        @RequestParam query: String
    ): List<SupportCustomerSearchResultResponse> =
        support.searchCustomers(currentUser(authentication), query)

    @GetMapping("/queue")
    fun queue(authentication: Authentication): SupportQueueResponse =
        support.queue(currentUser(authentication))

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

    @GetMapping("/customers/{publicId}/chat")
    fun customerChat(
        authentication: Authentication,
        @PathVariable publicId: String
    ): SupportChatResponse =
        support.adminChat(currentUser(authentication), publicId)

    @PostMapping("/customers/{publicId}/chat/read")
    fun markChatRead(
        authentication: Authentication,
        @PathVariable publicId: String
    ): SupportChatResponse =
        support.markChatRead(currentUser(authentication), publicId)

    @PostMapping("/customers/{publicId}/chat/messages")
    fun sendCustomerChatMessage(
        authentication: Authentication,
        @PathVariable publicId: String,
        @Valid @RequestBody request: CreateSupportMessageRequest
    ): SupportMessageResponse =
        support.sendAdminChatMessage(currentUser(authentication), publicId, request.message)

    @GetMapping("/customers/{publicId}/context")
    fun customerContext(
        authentication: Authentication,
        @PathVariable publicId: String
    ): AdminUserDetailResponse =
        adminService.supportCustomerContext(currentUser(authentication), publicId)

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

    @PostMapping("/cases/{caseId}/ownership")
    fun takeOwnership(
        authentication: Authentication,
        @PathVariable caseId: String
    ): SupportAssignmentResponse =
        support.takeCaseOwnership(currentUser(authentication), caseId)

    @DeleteMapping("/cases/{caseId}/ownership")
    fun releaseOwnership(
        authentication: Authentication,
        @PathVariable caseId: String
    ): SupportAssignmentResponse =
        support.releaseCaseOwnership(currentUser(authentication), caseId)

    @PostMapping("/cases/{caseId}/notes")
    fun addNote(
        authentication: Authentication,
        @PathVariable caseId: String,
        @Valid @RequestBody request: CreateSupportNoteRequest
    ): SupportNoteResponse =
        support.addNote(currentUser(authentication), caseId, request)
}
