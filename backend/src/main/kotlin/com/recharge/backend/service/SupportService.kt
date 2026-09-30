package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.domain.*
import com.recharge.backend.repository.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant

@Service
class SupportService(
    private val cases: SupportCaseRepository,
    private val conversations: SupportConversationRepository,
    private val interactions: SupportInteractionRepository,
    private val notes: SupportNoteRepository,
    private val callRequests: SupportCallRequestRepository,
    private val events: SupportCaseEventRepository,
    private val users: UserRepository,
    private val roleAccess: RoleAccessService,
    private val overrides: UserPermissionOverrideRepository,
    private val voiceParticipants: VoiceCallParticipantRepository,
    private val voiceCalls: VoiceCallRepository,
    private val messages: SupportMessageRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    companion object {
        const val SUPPORT_VIEW = "SUPPORT_VIEW"
        const val SUPPORT_MANAGE = "SUPPORT_MANAGE"
        const val REQUEST_SUPPORT_CALL = "REQUEST_SUPPORT_CALL"
        private const val PENDING = "PENDING"
        private const val IN_PROGRESS = "IN_PROGRESS"
        private const val COMPLETED = "COMPLETED"
        private const val DECLINED = "DECLINED"
        private const val CANCELLED = "CANCELLED"
        private const val EXPIRED = "EXPIRED"
        private const val OPEN = "OPEN"
        private const val RESOLVED = "RESOLVED"
        private const val CLOSED = "CLOSED"
        private const val CALLBACK_WINDOW_MINUTES = 15L
    }

    @Transactional
    fun requestCustomerCall(customer: UserEntity, reason: String?): SupportCallRequestResponse {
        roleAccess.requirePermission(customer, REQUEST_SUPPORT_CALL)
        ensureClient(customer)

        val customerId = requireNotNull(customer.id)
        val activeParticipant = voiceParticipants.findByUserId(customerId).orElse(null)
        if (activeParticipant != null) {
            val activeCall = voiceCalls.findByCallId(activeParticipant.callId).orElse(null)
            if (activeCall != null && activeCall.status !in setOf("DECLINED", "MISSED", "CANCELLED", "ENDED")) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "You already have an active support call")
            }
        }
        val now = Instant.now()
        val existing = callRequests.findFirstByCustomerUserIdAndStatusOrderByRequestedAtDesc(customerId, PENDING).orElse(null)
        if (existing != null && existing.expiresAt.isAfter(now)) return toRequestResponse(existing)

        existing?.let {
            it.status = EXPIRED
            it.reviewedAt = now
            callRequests.save(it)
        }

        var conversation = conversations.findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(customerId, OPEN).orElse(null)
        var supportCase = conversation?.caseId?.let { cases.findById(it).orElse(null) }

        if (supportCase == null) {
            supportCase = cases.save(
                SupportCaseEntity(
                    customerUserId = customerId,
                    subject = if (conversation != null) "Customer requested a support callback" else "Customer requested a support call",
                    category = if (conversation != null) "CHAT_CALLBACK" else "CALLBACK",
                    priority = "NORMAL",
                    status = OPEN,
                    source = if (conversation != null) "CUSTOMER_CHAT_CALLBACK" else "CUSTOMER_CALL_REQUEST",
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            supportCase.status = OPEN
            supportCase.resolvedAt = null
            supportCase.updatedAt = now
            cases.save(supportCase)
        }

        if (conversation == null) {
            conversation = conversations.save(
                SupportConversationEntity(
                    caseId = supportCase.id,
                    customerUserId = customerId,
                    status = OPEN,
                    startedAt = now,
                    lastActivityAt = now
                )
            )
        } else {
            conversation.caseId = supportCase.id
            conversation.status = OPEN
            conversation.closedAt = null
            conversation.lastActivityAt = now
            conversations.save(conversation)
        }

        val request = callRequests.save(
            SupportCallRequestEntity(
                customerUserId = customerId,
                caseId = supportCase.id,
                conversationId = conversation.id,
                status = PENDING,
                reason = reason?.trim()?.takeIf { it.isNotBlank() }?.take(500),
                requestedAt = now,
                expiresAt = now.plusSeconds(CALLBACK_WINDOW_MINUTES * 60)
            )
        )
        recordCaseEvent(supportCase, conversation.id, customerId, "CALL_REQUESTED", "CUSTOMER", "VOICE", "Support callback requested", request.requestId)
        return toRequestResponse(request)
    }

    @Transactional
    fun cancelCustomerCallRequest(customer: UserEntity, requestId: String): SupportCallRequestResponse {
        ensureClient(customer)
        val request = callRequests.findByRequestIdForUpdate(requestId.trim()).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Support call request not found")
        }
        val customerId = requireNotNull(customer.id)
        if (request.customerUserId != customerId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "This call request does not belong to your account")
        }
        if (request.status == PENDING) {
            request.status = CANCELLED
            request.reviewedAt = Instant.now()
            callRequests.save(request)
        }
        return toRequestResponse(request)
    }

    fun pendingRequests(viewer: UserEntity): List<SupportCallRequestResponse> {
        roleAccess.requirePermission(viewer, SUPPORT_VIEW)
        val now = Instant.now()
        expirePendingRequests(now)
        return callRequests.findAllByStatusOrderByRequestedAtAsc(PENDING).map(::toRequestResponse)
    }

    @Transactional(readOnly = true)
    fun queue(viewer: UserEntity): SupportQueueResponse {
        roleAccess.requirePermission(viewer, SUPPORT_VIEW)
        roleAccess.requirePermission(viewer, "VIEW_USER_DETAIL")
        expirePendingRequests(Instant.now())

        data class Work(
            val customerId: Long,
            var caseEntity: SupportCaseEntity? = null,
            var callback: SupportCallRequestEntity? = null,
            var unreadMessages: Int = 0,
            var lastActivityAt: Instant? = null
        )

        val work = linkedMapOf<Long, Work>()

        fun item(customerId: Long): Work = work.getOrPut(customerId) { Work(customerId) }

        callRequests.findAllByStatusOrderByRequestedAtAsc(PENDING).forEach { request ->
            val current = item(request.customerUserId)
            current.callback = request
            current.lastActivityAt = maxInstant(current.lastActivityAt, request.requestedAt)
            if (request.caseId != null) {
                current.caseEntity = cases.findById(request.caseId!!).orElse(null)
            }
        }

        cases.findAllByStatusOrderByUpdatedAtDesc(OPEN).forEach { supportCase ->
            val current = item(supportCase.customerUserId)
            if (current.caseEntity == null || supportCase.updatedAt.isAfter(current.caseEntity!!.updatedAt)) {
                current.caseEntity = supportCase
            }
            current.lastActivityAt = maxInstant(current.lastActivityAt, supportCase.updatedAt)
        }

        messages.findAllBySenderTypeAndStaffReadAtIsNullOrderByCreatedAtDesc("CUSTOMER").forEach { message ->
            val current = item(message.customerUserId)
            current.unreadMessages += 1
            current.lastActivityAt = maxInstant(current.lastActivityAt, message.createdAt)
            if (current.caseEntity == null && message.caseId != null) {
                current.caseEntity = cases.findById(message.caseId!!).orElse(null)
            }
        }

        val customerIds = work.keys
        if (customerIds.isEmpty()) {
            return SupportQueueResponse(0, 0, 0, 0, 0, emptyList())
        }

        val customerMap = users.findAllById(customerIds).associateBy { requireNotNull(it.id) }
        val visible = customerMap.values.filter { roleAccess.canView(viewer, it) }
        val staffIds = visible.flatMap { customer ->
            val caseId = work[requireNotNull(customer.id)]?.caseEntity?.assignedUserId
            listOfNotNull(caseId)
        }.toSet()
        val staffMap = if (staffIds.isEmpty()) emptyMap() else users.findAllById(staffIds).associateBy { requireNotNull(it.id) }

        val items = visible.mapNotNull { customer ->
            val current = work[requireNotNull(customer.id)] ?: return@mapNotNull null
            val supportCase = current.caseEntity
            val callback = current.callback
            val assignedId = callback?.assignedUserId ?: supportCase?.assignedUserId
            val attentionReason = when {
                callback != null -> "Callback waiting"
                current.unreadMessages > 0 -> "New customer message"
                supportCase?.priority in setOf("URGENT", "HIGH") -> "High-priority case"
                else -> "Open case"
            }
            val source = when {
                callback != null -> "CALLBACK"
                current.unreadMessages > 0 -> "CHAT"
                else -> "CASE"
            }
            SupportQueueItemResponse(
                customerPublicId = customer.publicId,
                customerName = customer.name,
                customerMobile = customer.mobile,
                caseId = supportCase?.caseId,
                subject = supportCase?.subject,
                category = supportCase?.category,
                priority = supportCase?.priority,
                caseStatus = supportCase?.status,
                assignedUserPublicId = assignedId?.let { staffMap[it]?.publicId },
                assignedUserName = assignedId?.let { staffMap[it]?.name },
                source = source,
                attentionReason = attentionReason,
                unreadMessages = current.unreadMessages,
                lastActivityAt = current.lastActivityAt?.toString(),
                pendingCallback = callback?.let(::toRequestResponse)
            )
        }.sortedWith(
            compareBy<SupportQueueItemResponse>(
                { if (it.pendingCallback != null) 0 else if (it.priority.equals("URGENT", true)) 1 else if (it.priority.equals("HIGH", true)) 2 else if (it.unreadMessages > 0) 3 else 4 },
                { if (it.assignedUserPublicId == viewer.publicId) 0 else if (it.assignedUserPublicId == null) 1 else 2 },
                { it.pendingCallback?.requestedAt ?: it.lastActivityAt ?: "" }
            )
        ).take(75)

        return SupportQueueResponse(
            total = items.size,
            callbacks = items.count { it.pendingCallback != null },
            unreadChats = items.count { it.unreadMessages > 0 },
            unassigned = items.count { it.assignedUserPublicId == null },
            assignedToViewer = items.count { it.assignedUserPublicId == viewer.publicId },
            items = items
        )
    }

    fun request(viewer: UserEntity, requestId: String): SupportCallRequestResponse {
        roleAccess.requirePermission(viewer, SUPPORT_VIEW)
        val request = requestById(requestId)
        val customer = users.findById(request.customerUserId).orElseThrow { IllegalArgumentException("Customer not found") }
        roleAccess.requireCanView(viewer, customer)
        if (request.status == PENDING && request.expiresAt.isBefore(Instant.now())) {
            expirePendingRequests(Instant.now())
        }
        return toRequestResponse(requestById(requestId))
    }

    @Transactional
    fun claimSupportRequestForCall(viewer: UserEntity, requestId: String, targetPublicId: String): SupportCallRequestEntity {
        roleAccess.requirePermission(viewer, SUPPORT_MANAGE)
        val request = callRequests.findByRequestIdForUpdate(requestId.trim()).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Support call request not found")
        }
        val target = clientByPublicId(targetPublicId)
        if (request.customerUserId != requireNotNull(target.id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This callback request belongs to another customer")
        }
        val now = Instant.now()
        if (request.status != PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This callback request is no longer pending")
        }
        if (request.expiresAt.isBefore(now)) {
            request.status = EXPIRED
            request.reviewedAt = now
            request.outcome = "EXPIRED"
            request.outcomeAt = now
            callRequests.save(request)
            closeCase(request.caseId, "Callback request expired")
            throw ResponseStatusException(HttpStatus.CONFLICT, "This callback request has expired")
        }
        request.assignedUserId = requireNotNull(viewer.id)
        request.claimedAt = now
        callRequests.save(request)
        request.caseId?.let { caseId ->
            cases.findById(caseId).orElse(null)?.let {
                it.assignedUserId = viewer.id
                it.updatedAt = now
                cases.save(it)
                recordCaseEvent(it, request.conversationId, viewer.id, "CALL_REQUEST_CLAIMED", "INTERNAL", "VOICE", "Support callback claimed", request.requestId)
            }
        }
        return request
    }

    fun declineRequest(viewer: UserEntity, requestId: String, note: String?): SupportCallRequestResponse {
        roleAccess.requirePermission(viewer, SUPPORT_MANAGE)
        val request = requestById(requestId)
        if (request.status != PENDING) {
            return toRequestResponse(request)
        }
        val now = Instant.now()
        request.status = DECLINED
        request.reviewedByUserId = requireNotNull(viewer.id)
        request.reviewedAt = now
        request.reviewNote = note?.trim()?.takeIf { it.isNotBlank() }?.take(1000)
        request.outcome = "DECLINED_BY_SUPPORT"
        request.outcomeAt = now
        callRequests.save(request)
        request.caseId?.let { caseId ->
            cases.findById(caseId).orElse(null)?.let {
                recordCaseEvent(it, request.conversationId, viewer.id, "CALL_REQUEST_DECLINED", "CUSTOMER", "VOICE", "Support callback request declined", request.requestId)
            }
        }
        closeCase(request.caseId, request.reviewNote ?: "Support call request declined")
        return toRequestResponse(request)
    }

    fun customer(viewer: UserEntity, publicId: String): SupportCustomerResponse {
        roleAccess.requirePermission(viewer, SUPPORT_VIEW)
        val customer = visibleClient(viewer, publicId)
        return customerResponse(customer, includeInternalNotes = true)
    }

    @Transactional
    fun customerChat(customer: UserEntity): SupportChatResponse {
        ensureClient(customer)
        val now = Instant.now()
        val conversation = conversations.findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(requireNotNull(customer.id), OPEN)
            .orElse(null)
            ?: conversations.findAllByCustomerUserIdOrderByLastActivityAtDesc(requireNotNull(customer.id)).firstOrNull()

        if (conversation == null) {
            return SupportChatResponse(
                conversationId = null,
                caseId = null,
                status = OPEN,
                messages = emptyList(),
                unreadForCustomer = 0,
                unreadForStaff = 0,
                callbackRequestEnabled = callbackRequestEnabled(customer),
                pendingCallbackRequest = callRequests.findFirstByCustomerUserIdAndStatusOrderByRequestedAtDesc(requireNotNull(customer.id), PENDING).orElse(null)?.let(::toRequestResponse)
            )
        }

        messages.findAllByConversationIdOrderByCreatedAtAsc(requireNotNull(conversation.id))
            .filter { it.senderType in setOf("STAFF", "AI") && it.customerReadAt == null }
            .forEach {
                it.customerReadAt = now
                messages.save(it)
            }

        val messageList = messages.findAllByConversationIdOrderByCreatedAtAsc(requireNotNull(conversation.id))
        return SupportChatResponse(
            conversationId = conversation.conversationId,
            caseId = conversation.caseId?.let { cases.findById(it).orElse(null)?.caseId },
            status = conversation.status,
            messages = messageList.map(::toMessageResponse),
            unreadForCustomer = messageList.count { it.senderType in setOf("STAFF", "AI") && it.customerReadAt == null },
            unreadForStaff = messageList.count { it.senderType == "CUSTOMER" && it.staffReadAt == null },
            callbackRequestEnabled = callbackRequestEnabled(customer),
            pendingCallbackRequest = callRequests.findFirstByCustomerUserIdAndStatusOrderByRequestedAtDesc(requireNotNull(customer.id), PENDING).orElse(null)?.let(::toRequestResponse)
        )
    }

    @Transactional
    fun sendCustomerChatMessage(customer: UserEntity, messageText: String): SupportMessageResponse {
        ensureClient(customer)
        val message = messageText.trim().take(4000)
        if (message.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be empty")
        }

        val now = Instant.now()
        val customerId = requireNotNull(customer.id)
        var conversation = conversations.findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(customerId, OPEN).orElse(null)

        if (conversation == null) {
            val supportCase = cases.findFirstByCustomerUserIdAndStatusInOrderByUpdatedAtDesc(customerId, listOf(OPEN, RESOLVED)).orElse(null)
                ?: cases.save(
                    SupportCaseEntity(
                        customerUserId = customerId,
                        subject = "Customer chat with mPay Support",
                        category = "CHAT",
                        priority = "NORMAL",
                        status = OPEN,
                        source = "CUSTOMER_CHAT",
                        createdAt = now,
                        updatedAt = now
                    )
                )
            if (supportCase.status != OPEN) {
                supportCase.status = OPEN
                supportCase.resolvedAt = null
                supportCase.updatedAt = now
                cases.save(supportCase)
            }
            conversation = conversations.save(
                SupportConversationEntity(
                    caseId = supportCase.id,
                    customerUserId = customerId,
                    status = OPEN,
                    startedAt = now,
                    lastActivityAt = now
                )
            )
        }

        val saved = messages.save(
            SupportMessageEntity(
                conversationId = requireNotNull(conversation.id),
                caseId = conversation.caseId,
                customerUserId = customerId,
                senderUserId = customerId,
                senderType = "CUSTOMER",
                message = message,
                createdAt = now,
                customerReadAt = now
            )
        )
        conversation.lastActivityAt = now
        conversation.status = OPEN
        conversations.save(conversation)
        conversation.caseId?.let { caseId ->
            cases.findById(caseId).orElse(null)?.let {
                it.status = OPEN
                it.updatedAt = now
                cases.save(it)
                recordCaseEvent(it, conversation.id, customerId, "CUSTOMER_MESSAGE", "CUSTOMER", "CHAT", "Customer sent a support chat message", saved.messageId)
            }
        }

        eventPublisher.publishEvent(
            SupportCustomerMessageCreatedEvent(
                messageId = saved.messageId,
                conversationId = requireNotNull(conversation.id),
                caseId = conversation.caseId,
                customerUserId = customerId,
                message = message
            )
        )
        return toMessageResponse(saved)
    }

    @Transactional
    fun appendAutomatedSupportMessage(
        conversationId: Long,
        caseId: Long?,
        customerUserId: Long,
        messageText: String
    ): SupportMessageResponse {
        val message = messageText.trim().take(4000)
        if (message.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be empty")
        }

        val conversation = conversations.findById(conversationId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Support conversation not found")
        }
        if (conversation.customerUserId != customerUserId || conversation.status != OPEN) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Support conversation is no longer active")
        }

        val now = Instant.now()
        val saved = messages.save(
            SupportMessageEntity(
                conversationId = conversationId,
                caseId = caseId ?: conversation.caseId,
                customerUserId = customerUserId,
                senderUserId = null,
                senderType = "AI",
                message = message,
                createdAt = now
            )
        )
        conversation.lastActivityAt = now
        conversations.save(conversation)

        val resolvedCaseId = caseId ?: conversation.caseId
        resolvedCaseId?.let { supportCaseId ->
            cases.findById(supportCaseId).orElse(null)?.let {
                if (it.status != OPEN) {
                    it.status = OPEN
                    it.resolvedAt = null
                }
                it.updatedAt = now
                cases.save(it)
                recordCaseEvent(it, conversationId, null, "AI_MESSAGE", "CUSTOMER", "CHAT", "mPay AI replied in support chat", saved.messageId)
            }
        }

        return toMessageResponse(saved)
    }

    @Transactional
    fun adminChat(viewer: UserEntity, publicId: String): SupportChatResponse {
        roleAccess.requirePermission(viewer, SUPPORT_VIEW)
        val customer = visibleClient(viewer, publicId)
        val conversation = conversations.findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(requireNotNull(customer.id), OPEN)
            .orElseGet {
                conversations.findAllByCustomerUserIdOrderByLastActivityAtDesc(requireNotNull(customer.id)).firstOrNull()
            }
            ?: return SupportChatResponse(null, null, OPEN, emptyList(), 0, 0)

        val messageList = messages.findAllByConversationIdOrderByCreatedAtAsc(requireNotNull(conversation.id))
        return SupportChatResponse(
            conversationId = conversation.conversationId,
            caseId = conversation.caseId?.let { cases.findById(it).orElse(null)?.caseId },
            status = conversation.status,
            messages = messageList.map(::toMessageResponse),
            unreadForCustomer = messageList.count { it.senderType == "STAFF" && it.customerReadAt == null },
            unreadForStaff = messageList.count { it.senderType == "CUSTOMER" && it.staffReadAt == null }
        )
    }

    @Transactional
    fun markChatRead(viewer: UserEntity, publicId: String): SupportChatResponse {
        roleAccess.requirePermission(viewer, SUPPORT_VIEW)
        val customer = visibleClient(viewer, publicId)
        val conversation = conversations.findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(requireNotNull(customer.id), OPEN)
            .orElse(null)
            ?: return SupportChatResponse(null, null, OPEN, emptyList(), 0, 0)

        val now = Instant.now()
        messages.findAllByConversationIdOrderByCreatedAtAsc(requireNotNull(conversation.id))
            .filter { it.senderType == "CUSTOMER" && it.staffReadAt == null }
            .forEach {
                it.staffReadAt = now
                messages.save(it)
            }
        return adminChat(viewer, publicId)
    }

    @Transactional
    fun sendAdminChatMessage(viewer: UserEntity, publicId: String, messageText: String): SupportMessageResponse {
        roleAccess.requirePermission(viewer, SUPPORT_MANAGE)
        val customer = visibleClient(viewer, publicId)
        val message = messageText.trim().take(4000)
        if (message.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be empty")
        }

        val now = Instant.now()
        val customerId = requireNotNull(customer.id)
        var conversation = conversations.findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(customerId, OPEN).orElse(null)
        if (conversation == null) {
            val supportCase = cases.save(
                SupportCaseEntity(
                    customerUserId = customerId,
                    subject = "Customer support chat",
                    category = "CHAT",
                    priority = "NORMAL",
                    status = OPEN,
                    source = "SUPPORT_CHAT",
                    assignedUserId = viewer.id,
                    createdAt = now,
                    updatedAt = now
                )
            )
            conversation = conversations.save(
                SupportConversationEntity(
                    caseId = supportCase.id,
                    customerUserId = customerId,
                    status = OPEN,
                    startedAt = now,
                    lastActivityAt = now
                )
            )
        }

        val saved = messages.save(
            SupportMessageEntity(
                conversationId = requireNotNull(conversation.id),
                caseId = conversation.caseId,
                customerUserId = customerId,
                senderUserId = requireNotNull(viewer.id),
                senderType = "STAFF",
                message = message,
                createdAt = now
            )
        )
        conversation.lastActivityAt = now
        conversation.status = OPEN
        conversations.save(conversation)
        conversation.caseId?.let { caseId ->
            cases.findById(caseId).orElse(null)?.let {
                it.status = OPEN
                it.assignedUserId = viewer.id
                it.updatedAt = now
                cases.save(it)
                recordCaseEvent(it, conversation.id, viewer.id, "SUPPORT_MESSAGE", "CUSTOMER", "CHAT", "mPay Support replied in chat", saved.messageId)
            }
        }
        return toMessageResponse(saved)
    }

    fun customerOverview(customer: UserEntity): CustomerSupportOverviewResponse {
        ensureClient(customer)
        val customerId = requireNotNull(customer.id)
        expirePendingRequests(Instant.now())
        return CustomerSupportOverviewResponse(
            callbackRequestEnabled = callbackRequestEnabled(customer),
            pendingRequest = callRequests.findFirstByCustomerUserIdAndStatusOrderByRequestedAtDesc(customerId, PENDING).orElse(null)?.let(::toRequestResponse),
            cases = cases.findAllByCustomerUserIdOrderByUpdatedAtDesc(customerId).take(20).map { toCaseResponse(it, customer) },
            interactions = interactions.findAllByCustomerUserIdOrderByStartedAtDesc(customerId).take(50).map(::toInteractionResponse),
            customerNotes = notes.findAllByCustomerUserIdAndVisibilityOrderByCreatedAtDesc(customerId, "CUSTOMER").take(50).map(::toNoteResponse),
            events = events.findAllByCustomerUserIdAndVisibilityOrderByCreatedAtDesc(customerId, "CUSTOMER").take(100).map(::toEventResponse)
        )
    }

    fun callbackAccess(viewer: UserEntity, publicId: String): CustomerCallbackAccessResponse {
        roleAccess.requirePermission(viewer, "MANAGE_CALL_ACCESS")
        val customer = clientByPublicId(publicId)
        return CustomerCallbackAccessResponse(customer.publicId, callbackRequestEnabled(customer))
    }

    @Transactional
    fun setCallbackAccess(viewer: UserEntity, publicId: String, enabled: Boolean): CustomerCallbackAccessResponse {
        roleAccess.requirePermission(viewer, "MANAGE_CALL_ACCESS")
        val customer = clientByPublicId(publicId)
        val userId = requireNotNull(customer.id)
        val existing = overrides.findByUserIdAndPermissionIgnoreCase(userId, REQUEST_SUPPORT_CALL)
        if (enabled) {
            val now = Instant.now()
            if (existing == null) {
                overrides.save(
                    UserPermissionOverrideEntity(
                        userId = userId,
                        permission = REQUEST_SUPPORT_CALL,
                        allowed = true,
                        grantedByUserId = requireNotNull(viewer.id),
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } else {
                existing.allowed = true
                existing.grantedByUserId = requireNotNull(viewer.id)
                existing.updatedAt = now
                overrides.save(existing)
            }
        } else if (existing != null) {
            overrides.delete(existing)
            callRequests.findFirstByCustomerUserIdAndStatusOrderByRequestedAtDesc(userId, PENDING).orElse(null)?.let {
                it.status = CANCELLED
                it.reviewedByUserId = requireNotNull(viewer.id)
                it.reviewedAt = Instant.now()
                it.reviewNote = "Customer callback permission disabled"
                callRequests.save(it)
            }
        }
        return CustomerCallbackAccessResponse(customer.publicId, enabled)
    }

    @Transactional
    fun recordVoiceCallStarted(call: VoiceCallEntity, actor: UserEntity, supportRequestId: String? = null) {
        if (interactions.findByVoiceCallId(call.callId).isPresent) return

        val customerId = call.calleeUserId.takeIf { it == actor.id } ?: call.calleeUserId
        val request = supportRequestId?.let {
            callRequests.findByRequestId(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Support call request not found")
            }
        }
        if (request != null && request.status != PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This callback request is no longer pending")
        }
        val caseEntity = request?.caseId?.let { cases.findById(it).orElse(null) }
            ?: cases.findFirstByCustomerUserIdAndStatusInOrderByUpdatedAtDesc(customerId, listOf(OPEN, RESOLVED)).orElse(null)
            ?: cases.save(
                SupportCaseEntity(
                    customerUserId = customerId,
                    subject = "Voice support call",
                    category = "VOICE",
                    priority = "NORMAL",
                    status = OPEN,
                    source = "STAFF_CALL",
                    createdAt = call.createdAt,
                    updatedAt = call.createdAt
                )
            )
        if (caseEntity.status != OPEN) {
            caseEntity.status = OPEN
            caseEntity.resolvedAt = null
            caseEntity.resolutionCode = null
            caseEntity.resolutionNote = null
            caseEntity.updatedAt = call.createdAt
            cases.save(caseEntity)
        }
        val conversation = request?.conversationId?.let { conversations.findById(it).orElse(null) }
            ?: conversations.findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(customerId, OPEN).orElse(null)
            ?: conversations.save(
                SupportConversationEntity(
                    caseId = caseEntity.id,
                    customerUserId = customerId,
                    status = OPEN,
                    startedAt = call.createdAt,
                    lastActivityAt = call.createdAt
                )
            )
        if (conversation.caseId == null) {
            conversation.caseId = caseEntity.id
            conversations.save(conversation)
        }

        val interaction = interactions.save(
            SupportInteractionEntity(
                conversationId = requireNotNull(conversation.id),
                caseId = caseEntity?.id,
                customerUserId = customerId,
                actorUserId = actor.id,
                channel = "VOICE",
                direction = "OUTBOUND",
                status = "RINGING",
                startedAt = call.createdAt,
                voiceCallId = call.callId
            )
        )
        conversation.lastActivityAt = Instant.now()
        conversations.save(conversation)

        if (request != null) {
            val now = Instant.now()
            request.status = IN_PROGRESS
            request.voiceCallId = call.callId
            request.reviewedByUserId = actor.id
            request.assignedUserId = actor.id
            request.claimedAt = request.claimedAt ?: now
            request.reviewedAt = now
            callRequests.save(request)
        }

        caseEntity?.let {
            it.updatedAt = Instant.now()
            cases.save(it)
            recordCaseEvent(it, conversation.id, actor.id, "VOICE_CALL_STARTED", "CUSTOMER", "VOICE", "mPay support started a voice call", call.callId)
        }
    }

    @Transactional
    fun recordVoiceCallEnded(call: VoiceCallEntity) {
        val interaction = interactions.findByVoiceCallId(call.callId).orElse(null) ?: return
        interaction.status = call.status
        interaction.endedAt = call.endedAt
        interaction.durationSeconds = if (call.connectedAt != null && call.endedAt != null) Duration.between(call.connectedAt, call.endedAt).seconds.coerceAtLeast(0) else 0L
        interaction.ringDurationSeconds = call.endedAt?.let { ended -> Duration.between(call.createdAt, call.acceptedAt ?: ended).seconds.coerceAtLeast(0) }
        interaction.handlingDurationSeconds = call.endedAt?.let { ended -> Duration.between(call.createdAt, ended).seconds.coerceAtLeast(0) }
        interaction.outcome = when (call.status) {
            "ENDED" -> when {
                call.connectedAt != null -> "ANSWERED"
                call.endedReason == "CONNECT_TIMEOUT" -> "CONNECT_FAILED"
                else -> "ENDED_BEFORE_CONNECT"
            }
            "DECLINED" -> "CUSTOMER_DECLINED"
            "MISSED" -> "NO_ANSWER"
            "CANCELLED" -> "CANCELLED"
            else -> call.endedReason
        }
        interactions.save(interaction)

        val conversation = conversations.findById(interaction.conversationId).orElse(null)
        conversation?.let {
            it.lastActivityAt = call.endedAt ?: Instant.now()
            conversations.save(it)
        }

        callRequests.findAllByCustomerUserIdOrderByRequestedAtDesc(interaction.customerUserId)
            .firstOrNull { it.voiceCallId == call.callId }
            ?.let {
                val now = call.endedAt ?: Instant.now()
                it.status = when (call.status) {
                    "DECLINED" -> DECLINED
                    "CANCELLED" -> CANCELLED
                    else -> COMPLETED
                }
                it.outcome = interaction.outcome
                it.outcomeAt = now
                it.reviewedAt = it.reviewedAt ?: now
                callRequests.save(it)
            }

        interaction.caseId?.let { caseId ->
            val caseEntity = cases.findById(caseId).orElse(null)
            caseEntity?.let {
                it.updatedAt = call.endedAt ?: Instant.now()
                if (call.status in setOf("DECLINED", "MISSED", "CANCELLED")) {
                    // Keep the customer issue open so the agent can follow up.
                    if (it.status == CLOSED) it.status = OPEN
                }
                cases.save(it)
                recordCaseEvent(it, interaction.conversationId, interaction.actorUserId, "VOICE_CALL_ENDED", "CUSTOMER", "VOICE", "Voice support call ended", call.callId)
            }
        }
    }

    @Transactional
    fun updateCase(viewer: UserEntity, caseId: String, request: UpdateSupportCaseRequest): SupportCaseResponse {
        roleAccess.requirePermission(viewer, SUPPORT_MANAGE)
        val entity = cases.findByCaseId(caseId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Support case not found")
        }
        val customer = visibleClient(viewer, users.findById(entity.customerUserId).orElseThrow { IllegalArgumentException("Customer not found") }.publicId)
        val status = request.status.trim().uppercase()
        if (status !in setOf(OPEN, RESOLVED, CLOSED)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be OPEN, RESOLVED, or CLOSED")
        }
        val now = Instant.now()
        entity.status = status
        entity.updatedAt = now
        if (status == RESOLVED || status == CLOSED) entity.resolvedAt = now else entity.resolvedAt = null
        entity.resolutionCode = request.resolutionCode?.trim()?.takeIf { it.isNotBlank() }?.take(100)
        entity.resolutionNote = request.resolutionNote?.trim()?.takeIf { it.isNotBlank() }?.take(1200)
        cases.save(entity)
        recordCaseEvent(entity, conversations.findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(entity.customerUserId, OPEN).orElse(null)?.id, viewer.id, "CASE_STATUS_CHANGED", "CUSTOMER", "SUPPORT", "Support case status changed to " + status, request.resolutionCode)
        markWrapUp(entity.id, now)
        return toCaseResponse(entity, customer)
    }

    @Transactional
    fun takeCaseOwnership(viewer: UserEntity, caseId: String): SupportAssignmentResponse {
        roleAccess.requirePermission(viewer, SUPPORT_MANAGE)
        val entity = cases.findByCaseId(caseId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Support case not found")
        }
        val customer = users.findById(entity.customerUserId).orElseThrow { IllegalArgumentException("Customer not found") }
        roleAccess.requireCanView(viewer, customer)
        if (entity.status != OPEN) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only open support cases can be assigned")
        }
        entity.assignedUserId = requireNotNull(viewer.id)
        entity.updatedAt = Instant.now()
        cases.save(entity)
        recordCaseEvent(entity, null, viewer.id, "CASE_ASSIGNED", "INTERNAL", "SUPPORT", "Support case assigned to " + (viewer.name ?: viewer.publicId), viewer.publicId)
        return SupportAssignmentResponse(entity.caseId, viewer.publicId, viewer.name)
    }

    @Transactional
    fun releaseCaseOwnership(viewer: UserEntity, caseId: String): SupportAssignmentResponse {
        roleAccess.requirePermission(viewer, SUPPORT_MANAGE)
        val entity = cases.findByCaseId(caseId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Support case not found")
        }
        val customer = users.findById(entity.customerUserId).orElseThrow { IllegalArgumentException("Customer not found") }
        roleAccess.requireCanView(viewer, customer)
        val currentAssigned = entity.assignedUserId
        val canRelease = currentAssigned == viewer.id || viewer.role.equals("ADMIN", true)
        if (!canRelease) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the case owner or an administrator can release this case")
        }
        entity.assignedUserId = null
        entity.updatedAt = Instant.now()
        cases.save(entity)
        recordCaseEvent(entity, null, viewer.id, "CASE_UNASSIGNED", "INTERNAL", "SUPPORT", "Support case released", viewer.publicId)
        return SupportAssignmentResponse(entity.caseId, null, null)
    }

    @Transactional
    fun addNote(viewer: UserEntity, caseId: String, request: CreateSupportNoteRequest): SupportNoteResponse {
        roleAccess.requirePermission(viewer, SUPPORT_MANAGE)
        val entity = cases.findByCaseId(caseId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Support case not found")
        }
        val customer = users.findById(entity.customerUserId).orElseThrow { IllegalArgumentException("Customer not found") }
        roleAccess.requireCanView(viewer, customer)
        val visibility = request.visibility.trim().uppercase()
        if (visibility !in setOf("INTERNAL", "CUSTOMER")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Visibility must be INTERNAL or CUSTOMER")
        }
        if (visibility == "CUSTOMER" && !roleAccess.hasPermission(viewer, SUPPORT_MANAGE)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Customer-visible support notes are not enabled for this account")
        }
        val note = notes.save(
            SupportNoteEntity(
                caseId = entity.id,
                conversationId = conversations.findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(entity.customerUserId, OPEN).orElse(null)?.id,
                customerUserId = entity.customerUserId,
                authorUserId = requireNotNull(viewer.id),
                visibility = visibility,
                note = request.note.trim(),
                createdAt = Instant.now()
            )
        )
        entity.updatedAt = Instant.now()
        cases.save(entity)
        recordCaseEvent(entity, note.conversationId, viewer.id, "NOTE_ADDED", visibility, "NOTE", if (visibility == "CUSTOMER") "Support added a customer-visible note" else "Support note added", note.id.toString())
        markWrapUp(entity.id, note.createdAt)
        return toNoteResponse(note)
    }

    private fun maxInstant(first: Instant?, second: Instant?): Instant? = when {
        first == null -> second
        second == null -> first
        first.isAfter(second) -> first
        else -> second
    }

    private fun customerResponse(customer: UserEntity, includeInternalNotes: Boolean): SupportCustomerResponse {
        val customerId = requireNotNull(customer.id)
        val notesList = if (includeInternalNotes) {
            notes.findAllByCustomerUserIdOrderByCreatedAtDesc(customerId).take(50)
        } else {
            notes.findAllByCustomerUserIdAndVisibilityOrderByCreatedAtDesc(customerId, "CUSTOMER").take(50)
        }
        val eventList = if (includeInternalNotes) {
            events.findAllByCustomerUserIdOrderByCreatedAtDesc(customerId).take(150)
        } else {
            events.findAllByCustomerUserIdAndVisibilityOrderByCreatedAtDesc(customerId, "CUSTOMER").take(100)
        }
        return SupportCustomerResponse(
            customerPublicId = customer.publicId,
            customerName = customer.name,
            mobile = customer.mobile,
            callbackRequestEnabled = callbackRequestEnabled(customer),
            pendingRequest = callRequests.findFirstByCustomerUserIdAndStatusOrderByRequestedAtDesc(customerId, PENDING).orElse(null)?.let(::toRequestResponse),
            openCases = cases.findAllByCustomerUserIdOrderByUpdatedAtDesc(customerId).filter { it.status != CLOSED }.take(30).map { toCaseResponse(it, customer) },
            interactions = interactions.findAllByCustomerUserIdOrderByStartedAtDesc(customerId).take(100).map(::toInteractionResponse),
            notes = notesList.map(::toNoteResponse),
            events = eventList.map(::toEventResponse)
        )
    }

    private fun markWrapUp(caseId: Long?, now: Instant) {
        if (caseId == null) return
        interactions.findFirstByCaseIdAndChannelOrderByStartedAtDesc(caseId, "VOICE").orElse(null)?.let { interaction ->
            if (interaction.endedAt != null && interaction.wrapUpCompletedAt == null) {
                interaction.wrapUpCompletedAt = now
                interactions.save(interaction)
            }
        }
    }

    private fun callbackRequestEnabled(customer: UserEntity): Boolean =
        roleAccess.hasPermission(customer, REQUEST_SUPPORT_CALL)

    private fun ensureClient(user: UserEntity) {
        if (!user.role.equals("CLIENT", true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Customer support is available only to client accounts")
        }
        if (!user.active || user.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This account is not active")
        }
    }

    private fun clientByPublicId(publicId: String): UserEntity {
        val target = users.findByPublicId(publicId.trim()).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Customer account not found")
        }
        if (!target.role.equals("CLIENT", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer account required")
        }
        return target
    }

    private fun visibleClient(viewer: UserEntity, publicId: String): UserEntity {
        val target = clientByPublicId(publicId)
        roleAccess.requireCanView(viewer, target)
        return target
    }

    private fun requestById(requestId: String): SupportCallRequestEntity =
        callRequests.findByRequestId(requestId.trim()).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Support call request not found")
        }

    private fun closeCase(caseId: Long?, note: String) {
        val entity = caseId?.let { cases.findById(it).orElse(null) } ?: return
        val now = Instant.now()
        entity.status = CLOSED
        entity.resolvedAt = now
        entity.updatedAt = now
        entity.resolutionCode = "CALL_REQUEST_DECLINED"
        entity.resolutionNote = note.take(1200)
        cases.save(entity)
        recordCaseEvent(entity, null, null, "CASE_AUTO_CLOSED", "CUSTOMER", "SUPPORT", note.take(500), null)
    }

    private fun recordCaseEvent(
        case: SupportCaseEntity,
        conversationId: Long?,
        actorUserId: Long?,
        eventType: String,
        visibility: String,
        channel: String?,
        summary: String,
        metadata: String?
    ) {
        events.save(
            SupportCaseEventEntity(
                caseId = requireNotNull(case.id),
                conversationId = conversationId,
                customerUserId = case.customerUserId,
                actorUserId = actorUserId,
                eventType = eventType,
                visibility = visibility,
                channel = channel,
                summary = summary.take(500),
                metadata = metadata?.take(5000),
                createdAt = Instant.now()
            )
        )
    }

    private fun touchConversation(conversationId: Long?, caseId: Long?) {
        val conversation = conversationId?.let { conversations.findById(it).orElse(null) }
            ?: caseId?.let { conversations.findFirstByCustomerUserIdAndStatusOrderByLastActivityAtDesc(
                cases.findById(it).orElse(null)?.customerUserId ?: 0, OPEN
            ).orElse(null) }
        conversation?.let {
            it.lastActivityAt = Instant.now()
            conversations.save(it)
        }
    }

    @Transactional
    @Scheduled(fixedDelayString = "\${MPAY_SUPPORT_REQUEST_SWEEP_MS:60000}")
    fun expireSupportRequests() {
        expirePendingRequests(Instant.now())
    }

    private fun expirePendingRequests(now: Instant) {
        callRequests.findAllByStatusAndExpiresAtBefore(PENDING, now).forEach {
            it.status = EXPIRED
            it.reviewedAt = now
            callRequests.save(it)
            closeCase(it.caseId, "Callback request expired")
        }
    }

    private fun toMessageResponse(entity: SupportMessageEntity): SupportMessageResponse =
        SupportMessageResponse(
            messageId = entity.messageId,
            senderType = entity.senderType,
            message = entity.message,
            createdAt = entity.createdAt.toString()
        )

    private fun toRequestResponse(entity: SupportCallRequestEntity): SupportCallRequestResponse {
        val customer = users.findById(entity.customerUserId).orElse(null)
        return SupportCallRequestResponse(
            requestId = entity.requestId,
            status = entity.status,
            reason = entity.reason,
            requestedAt = entity.requestedAt.toString(),
            expiresAt = entity.expiresAt.toString(),
            caseId = entity.caseId?.let { cases.findById(it).orElse(null)?.caseId },
            customerPublicId = customer?.publicId,
            customerName = customer?.name,
            customerMobile = customer?.mobile,
            voiceCallId = entity.voiceCallId,
            assignedUserPublicId = entity.assignedUserId?.let { users.findById(it).orElse(null)?.publicId },
            assignedUserName = entity.assignedUserId?.let { users.findById(it).orElse(null)?.name },
            claimedAt = entity.claimedAt?.toString(),
            outcome = entity.outcome
        )
    }

    private fun toCaseResponse(entity: SupportCaseEntity, customer: UserEntity) =
        SupportCaseResponse(
            caseId = entity.caseId,
            customerPublicId = customer.publicId,
            subject = entity.subject,
            category = entity.category,
            priority = entity.priority,
            status = entity.status,
            source = entity.source,
            assignedUserPublicId = entity.assignedUserId?.let { users.findById(it).orElse(null)?.publicId },
            assignedUserName = entity.assignedUserId?.let { users.findById(it).orElse(null)?.name },
            createdAt = entity.createdAt.toString(),
            updatedAt = entity.updatedAt.toString(),
            resolvedAt = entity.resolvedAt?.toString(),
            resolutionCode = entity.resolutionCode,
            resolutionNote = entity.resolutionNote
        )

    private fun toInteractionResponse(entity: SupportInteractionEntity): SupportInteractionResponse {
        val actor = entity.actorUserId?.let { users.findById(it).orElse(null) }
        val duration = entity.durationSeconds
        return SupportInteractionResponse(
            interactionId = entity.interactionId,
            caseId = entity.caseId?.let { cases.findById(it).orElse(null)?.caseId },
            channel = entity.channel,
            direction = entity.direction,
            status = entity.status,
            startedAt = entity.startedAt.toString(),
            endedAt = entity.endedAt?.toString(),
            durationSeconds = duration,
            durationLabel = duration?.let { formatDuration(it) },
            ringDurationSeconds = entity.ringDurationSeconds,
            ringDurationLabel = entity.ringDurationSeconds?.let { formatDuration(it) },
            handlingDurationSeconds = entity.handlingDurationSeconds,
            handlingDurationLabel = entity.handlingDurationSeconds?.let { formatDuration(it) },
            wrapUpCompletedAt = entity.wrapUpCompletedAt?.toString(),
            wrapUpDurationSeconds = if (entity.wrapUpCompletedAt != null && entity.endedAt != null) Duration.between(entity.endedAt, entity.wrapUpCompletedAt).seconds.coerceAtLeast(0) else null,
            wrapUpDurationLabel = if (entity.wrapUpCompletedAt != null && entity.endedAt != null) formatDuration(Duration.between(entity.endedAt, entity.wrapUpCompletedAt).seconds.coerceAtLeast(0)) else null,
            outcome = entity.outcome,
            voiceCallId = entity.voiceCallId,
            actorUserPublicId = actor?.publicId,
            actorName = actor?.name
        )
    }

    private fun toNoteResponse(entity: SupportNoteEntity): SupportNoteResponse {
        val author = users.findById(entity.authorUserId).orElse(null)
        return SupportNoteResponse(
            id = requireNotNull(entity.id),
            caseId = entity.caseId?.let { cases.findById(it).orElse(null)?.caseId },
            visibility = entity.visibility,
            note = entity.note,
            authorUserPublicId = author?.publicId,
            authorName = author?.name,
            createdAt = entity.createdAt.toString()
        )
    }

    private fun toEventResponse(entity: SupportCaseEventEntity): SupportCaseEventResponse {
        val actor = entity.actorUserId?.let { users.findById(it).orElse(null) }
        return SupportCaseEventResponse(
            eventId = entity.eventId,
            caseId = cases.findById(entity.caseId).orElse(null)?.caseId ?: "",
            eventType = entity.eventType,
            visibility = entity.visibility,
            channel = entity.channel,
            summary = entity.summary,
            actorUserPublicId = actor?.publicId,
            actorName = actor?.name,
            createdAt = entity.createdAt.toString()
        )
    }

    private fun formatDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
