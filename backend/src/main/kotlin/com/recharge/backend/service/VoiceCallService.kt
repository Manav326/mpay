package com.recharge.backend.service

import com.recharge.backend.api.CallIceServerResponse
import com.recharge.backend.api.VoiceCallResponse
import com.recharge.backend.api.VoiceCallSignalingTokenResponse
import com.recharge.backend.config.CallProperties
import com.recharge.backend.domain.EmployeeEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.domain.VoiceCallEntity
import com.recharge.backend.domain.VoiceCallParticipantEntity
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.repository.VoiceCallParticipantRepository
import com.recharge.backend.repository.VoiceCallRepository
import com.recharge.backend.security.JwtService
import jakarta.transaction.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class CallAccountRef(val accountType: String, val accountId: Long)

@Service
class VoiceCallService(
    private val calls: VoiceCallRepository,
    private val participants: VoiceCallParticipantRepository,
    private val users: UserRepository,
    private val employees: com.recharge.backend.repository.EmployeeRepository,
    private val roleAccess: RoleAccessService,
    private val push: CallPushService,
    private val jwtService: JwtService,
    private val properties: CallProperties,
    private val websocket: CallWebSocketRegistry,
    private val support: SupportService,
    private val employeeAudit: EmployeeAuditService
) {
    private companion object {
        const val RINGING = "RINGING"
        const val ACCEPTED = "ACCEPTED"
        const val CONNECTED = "CONNECTED"
        const val DECLINED = "DECLINED"
        const val MISSED = "MISSED"
        const val CANCELLED = "CANCELLED"
        const val ENDED = "ENDED"
        const val CONNECTED_DISCONNECT_GRACE_SECONDS = 20L
    }

    @Transactional
    fun create(caller: EmployeeEntity, targetPublicId: String, supportRequestId: String? = null): VoiceCallResponse {
        roleAccess.requirePermission(caller, "CALL_CUSTOMER")

        val target = users.findByPublicId(targetPublicId.trim()).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Customer account not found")
        }
        roleAccess.requireCanView(caller, target)

        if (!target.active || target.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This customer account is not available")
        }
        if (!target.role.equals("CLIENT", true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Voice support calls can only be placed to customer accounts")
        }

        val callerId = requireNotNull(caller.id)
        val targetId = requireNotNull(target.id)

        val now = Instant.now()

        if (supportRequestId != null) {
            support.claimSupportRequestForCall(caller, supportRequestId, target.publicId)
        }

        val callerParticipant = participants.findByAccountTypeAndAccountId("EMPLOYEE", callerId).orElse(null)
        if (callerParticipant != null) {
            val existingCall = calls.findByCallId(callerParticipant.callId).orElse(null)
            when {
                existingCall == null || isTerminal(existingCall.status) -> {
                    participants.delete(callerParticipant)
                }
                existingCall.status == RINGING && existingCall.ringingExpiresAt.isBefore(now) -> {
                    expireCall(existingCall)
                }
                existingCall.calleeUserId == targetId -> {
                    return response(existingCall)
                }
                else -> {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "You already have an active customer call")
                }
            }
        }

        val targetParticipant = participants.findByAccountTypeAndAccountId("USER", targetId).orElse(null)
        if (targetParticipant != null) {
            val existingCall = calls.findByCallId(targetParticipant.callId).orElse(null)
            when {
                existingCall == null || isTerminal(existingCall.status) -> {
                    participants.delete(targetParticipant)
                }
                existingCall.status == RINGING && existingCall.ringingExpiresAt.isBefore(now) -> {
                    expireCall(existingCall)
                }
                else -> {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "This customer is already in an active call")
                }
            }
        }
        val call = calls.saveAndFlush(
            VoiceCallEntity(
                callerEmployeeId = callerId,
                calleeUserId = targetId,
                status = RINGING,
                createdAt = now,
                ringingExpiresAt = now.plusSeconds(properties.ringingTimeoutSeconds.coerceAtLeast(10))
            )
        )

        try {
            participants.saveAllAndFlush(
                listOf(
                    VoiceCallParticipantEntity(callId = call.callId, accountType = "EMPLOYEE", accountId = callerId),
                    VoiceCallParticipantEntity(callId = call.callId, accountType = "USER", accountId = targetId)
                )
            )
        } catch (_: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "One of the accounts is already in an active call")
        }

        support.recordVoiceCallStarted(call, caller, supportRequestId)
        push.sendIncomingCall(targetId, call.callId, caller.name, call.ringingExpiresAt)
        employeeAudit.record(
            actor = caller,
            action = "CALL_STARTED",
            subjectType = "CUSTOMER",
            subjectId = target.publicId,
            summary = "Started a support call with " + (target.name ?: target.mobile) + ".",
            metadata = mapOf("callId" to call.callId)
        )
        return response(call)
    }

    @Transactional
    fun terminateActiveCallForEmployee(employeeId: Long, reason: String) {
        terminateActiveCallForAccount("EMPLOYEE", employeeId, reason)
    }

    @Transactional
    fun terminateActiveCallForUser(userId: Long, reason: String) {
        terminateActiveCallForAccount("USER", userId, reason)
    }

    private fun terminateActiveCallForAccount(accountType: String, accountId: Long, reason: String) {
        val participant = participants.findByAccountTypeAndAccountId(accountType, accountId).orElse(null) ?: return
        val call = calls.findByCallIdForUpdate(participant.callId).orElse(null) ?: run {
            participants.delete(participant)
            return
        }

        if (isTerminal(call.status)) {
            participants.deleteAllByCallId(call.callId)
            websocket.clearCall(call.callId)
            return
        }

        val otherAccount = otherParticipant(call, accountType, accountId)
        call.status = ENDED
        call.endedAt = Instant.now()
        call.endedByAccountId = accountId
        call.endedByAccountType = accountType
        call.endedReason = reason.take(80)
        calls.save(call)
        support.recordVoiceCallEnded(call)
        participants.deleteAllByCallId(call.callId)
        broadcastStatus(call)
        push.sendCallEnded(otherAccount.accountId, call.callId, call.status)
    }

    @Transactional
    fun accept(user: UserEntity, callId: String): VoiceCallResponse {
        val call = participantCallForUpdate(user, callId)
        requireCallee(call, user)

        if (call.status != RINGING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This call is no longer ringing")
        }
        if (call.ringingExpiresAt.isBefore(Instant.now())) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This call has expired")
        }

        call.status = ACCEPTED
        call.acceptedAt = Instant.now()
        calls.save(call)
        broadcastStatus(call)
        return response(call)
    }

    @Transactional
    fun decline(user: UserEntity, callId: String): VoiceCallResponse {
        val call = participantCallForUpdate(user, callId)
        requireCallee(call, user)

        if (call.status != RINGING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This call is no longer ringing")
        }

        call.status = DECLINED
        call.endedAt = Instant.now()
        call.endedByAccountId = requireNotNull(user.id)
        call.endedByAccountType = "USER"
        call.endedReason = "DECLINED"
        calls.save(call)
        support.recordVoiceCallEnded(call)

        participants.deleteAllByCallId(call.callId)
        broadcastStatus(call)
        push.sendCallEnded(call.callerEmployeeId, call.callId, call.status)
        return response(call)
    }

    @Transactional
    fun end(employee: EmployeeEntity, callId: String): VoiceCallResponse {
        val call = participantCallForUpdate(employee, callId)
        val employeeId = requireNotNull(employee.id)
        if (call.status in setOf(DECLINED, MISSED, CANCELLED, ENDED)) return response(call)
        if (call.callerEmployeeId != employeeId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the staff caller can end this call")
        }
        call.status = if (call.status == RINGING) CANCELLED else ENDED
        call.endedAt = Instant.now()
        call.endedByAccountId = employeeId
        call.endedByAccountType = "EMPLOYEE"
        call.endedReason = "HANGUP"
        calls.save(call)
        support.recordVoiceCallEnded(call)
        val otherAccount = otherParticipant(call, "EMPLOYEE", employeeId)
        participants.deleteAllByCallId(call.callId)
        broadcastStatus(call)
        push.sendCallEnded(otherAccount.accountId, call.callId, call.status)
        employeeAudit.record(
            actor = employee,
            action = "CALL_ENDED",
            subjectType = "CALL",
            subjectId = call.callId,
            summary = "Ended the support call.",
            metadata = mapOf("status" to call.status, "reason" to call.endedReason)
        )
        return response(call)
    }

    @Transactional
    fun end(user: UserEntity, callId: String): VoiceCallResponse {
        val call = participantCallForUpdate(user, callId)
        val userId = requireNotNull(user.id)

        if (call.status in setOf(DECLINED, MISSED, CANCELLED, ENDED)) return response(call)

        call.status = ENDED
        call.endedAt = Instant.now()
        call.endedByAccountId = userId
        call.endedByAccountType = "USER"
        call.endedReason = "HANGUP"
        calls.save(call)
        support.recordVoiceCallEnded(call)

        val otherAccount = otherParticipant(call, "USER", userId)
        participants.deleteAllByCallId(call.callId)
        broadcastStatus(call)
        push.sendCallEnded(otherAccount.accountId, call.callId, call.status)
        return response(call)
    }

    @Transactional
    fun markConnected(accountType: String, accountId: Long, callId: String): VoiceCallResponse {
        val call = participantCallForUpdateByAccountId(accountType, accountId, callId)
        if (call.status == ACCEPTED) {
            call.status = CONNECTED
            call.connectedAt = Instant.now()
            calls.save(call)
            broadcastStatus(call)
        }
        return response(call)
    }

    fun get(employee: EmployeeEntity, callId: String): VoiceCallResponse =
        response(participantCall(employee, callId))

    fun get(user: UserEntity, callId: String): VoiceCallResponse =
        response(participantCall(user, callId))

    @Transactional
    fun active(employee: EmployeeEntity): VoiceCallResponse? =
        activeAccount("EMPLOYEE", requireNotNull(employee.id))

    @Transactional
    fun active(user: UserEntity): VoiceCallResponse? =
        activeAccount("USER", requireNotNull(user.id))

    fun signalingToken(employee: EmployeeEntity, callId: String): VoiceCallSignalingTokenResponse {
        val call = participantCall(employee, callId)
        if (call.status !in setOf(ACCEPTED, CONNECTED)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Call audio is not active")
        }
        val token = jwtService.createCallSignalingToken(
            accountId = requireNotNull(employee.id),
            mobile = employee.mobile,
            role = employee.role,
            callId = call.callId,
            ttlSeconds = properties.signalingTokenTtlSeconds,
            accountType = "EMPLOYEE"
        )
        return VoiceCallSignalingTokenResponse(
            token = token,
            expiresInSeconds = properties.signalingTokenTtlSeconds.coerceAtLeast(30),
            websocketPath = properties.websocketPath
        )
    }

    fun signalingToken(user: UserEntity, callId: String): VoiceCallSignalingTokenResponse {
        val call = participantCall(user, callId)
        if (call.status !in setOf(ACCEPTED, CONNECTED)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Call audio is not active")
        }
        val token = jwtService.createCallSignalingToken(
            accountId = requireNotNull(user.id),
            mobile = user.mobile,
            role = user.role,
            callId = call.callId,
            ttlSeconds = properties.signalingTokenTtlSeconds,
            accountType = "USER"
        )
        return VoiceCallSignalingTokenResponse(
            token = token,
            expiresInSeconds = properties.signalingTokenTtlSeconds.coerceAtLeast(30),
            websocketPath = properties.websocketPath
        )
    }

    private fun activeAccount(accountType: String, accountId: Long): VoiceCallResponse? {
        val participant = participants.findByAccountTypeAndAccountId(accountType, accountId).orElse(null) ?: return null
        val call = calls.findByCallId(participant.callId).orElse(null) ?: return null
        if (call.status in setOf(DECLINED, MISSED, CANCELLED, ENDED)) return null
        if (call.status == RINGING && call.ringingExpiresAt.isBefore(Instant.now())) {
            expireCall(call)
            return null
        }
        return response(call)
    }

    @Transactional
    @Scheduled(fixedDelayString = "\${MPAY_CALL_EXPIRY_SWEEP_MS:5000}")
    fun expireRingingCalls() {
        calls.findAllByStatusAndRingingExpiresAtBefore(RINGING, Instant.now()).forEach { expireCall(it) }
    }

    @Transactional
    @Scheduled(fixedDelayString = "\${MPAY_CALL_EXPIRY_SWEEP_MS:5000}")
    fun expireDisconnectedConnectedCalls() {
        val now = Instant.now()
        calls.findAllByStatus(CONNECTED).forEach { call ->
            val disconnected = listOf(
                CallAccountRef("EMPLOYEE", call.callerEmployeeId),
                CallAccountRef("USER", call.calleeUserId)
            ).firstOrNull { !websocket.hasOpenSession(call.callId, it.accountType, it.accountId) }
                ?: return@forEach

            val disconnectedAt = websocket.disconnectedSinceOrMarkNow(
                call.callId,
                disconnected.accountType,
                disconnected.accountId
            )
            if (!disconnectedAt.plusSeconds(CONNECTED_DISCONNECT_GRACE_SECONDS).isAfter(now)) {
                expireDisconnectedConnectedCall(call)
            }
        }
    }

    @Transactional
    @Scheduled(fixedDelayString = "\${MPAY_CALL_EXPIRY_SWEEP_MS:5000}")
    fun expireUnconnectedCalls() {
        val cutoff = Instant.now().minusSeconds(properties.connectTimeoutSeconds.coerceAtLeast(15))
        calls.findAllByStatusAndAcceptedAtBefore(ACCEPTED, cutoff).forEach { expireUnconnectedCall(it) }
    }

    private fun expireDisconnectedConnectedCall(call: VoiceCallEntity) {
        val locked = calls.findByCallIdForUpdate(call.callId).orElse(null) ?: return
        if (locked.status != CONNECTED) return

        locked.status = ENDED
        locked.endedAt = Instant.now()
        locked.endedReason = "SIGNALING_DISCONNECT"
        calls.save(locked)
        support.recordVoiceCallEnded(locked)
        participants.deleteAllByCallId(locked.callId)
        broadcastStatus(locked)
    }

    private fun expireUnconnectedCall(call: VoiceCallEntity) {
        val locked = calls.findByCallIdForUpdate(call.callId).orElse(null) ?: return
        if (locked.status != ACCEPTED) return

        locked.status = ENDED
        locked.endedAt = Instant.now()
        locked.endedReason = "CONNECT_TIMEOUT"
        calls.save(locked)
        support.recordVoiceCallEnded(locked)
        participants.deleteAllByCallId(locked.callId)
        broadcastStatus(locked)
        push.sendCallEnded(locked.callerEmployeeId, locked.callId, locked.status)
        push.sendCallEnded(locked.calleeUserId, locked.callId, locked.status)
    }

    private fun isTerminal(status: String): Boolean =
        status in setOf(DECLINED, MISSED, CANCELLED, ENDED)

    private fun expireCall(call: VoiceCallEntity) {
        val locked = calls.findByCallIdForUpdate(call.callId).orElse(null) ?: return
        if (locked.status != RINGING) return

        locked.status = MISSED
        locked.endedAt = Instant.now()
        locked.endedReason = "TIMEOUT"
        calls.save(locked)
        support.recordVoiceCallEnded(locked)
        participants.deleteAllByCallId(locked.callId)
        broadcastStatus(locked)
        push.sendCallEnded(locked.callerEmployeeId, locked.callId, locked.status)
        push.sendCallEnded(locked.calleeUserId, locked.callId, locked.status)
    }

    private fun participantCall(employee: EmployeeEntity, callId: String): VoiceCallEntity =
        participantCallByAccountId("EMPLOYEE", requireNotNull(employee.id), callId)

    private fun participantCall(user: UserEntity, callId: String): VoiceCallEntity =
        participantCallByAccountId("USER", requireNotNull(user.id), callId)

    private fun participantCallForUpdate(employee: EmployeeEntity, callId: String): VoiceCallEntity =
        participantCallForUpdateByAccountId("EMPLOYEE", requireNotNull(employee.id), callId)

    private fun participantCallForUpdate(user: UserEntity, callId: String): VoiceCallEntity =
        participantCallForUpdateByAccountId("USER", requireNotNull(user.id), callId)

    private fun participantCallByAccountId(accountType: String, accountId: Long, callId: String): VoiceCallEntity {
        val call = calls.findByCallId(callId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Call not found")
        }
        val normalizedType = accountType.uppercase()
        val isParticipant = when (normalizedType) {
            "EMPLOYEE" -> call.callerEmployeeId == accountId
            "USER" -> call.calleeUserId == accountId
            else -> false
        }
        if (!isParticipant) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant in this call")
        }
        val participant = participants.findByAccountTypeAndAccountId(normalizedType, accountId).orElse(null)
        if (participant != null && participant.callId != callId && !isTerminal(call.status)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "You are already in another active call")
        }
        return call
    }

    private fun participantCallForUpdateByAccountId(accountType: String, accountId: Long, callId: String): VoiceCallEntity {
        val call = calls.findByCallIdForUpdate(callId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Call not found")
        }
        val normalizedType = accountType.uppercase()
        val isParticipant = when (normalizedType) {
            "EMPLOYEE" -> call.callerEmployeeId == accountId
            "USER" -> call.calleeUserId == accountId
            else -> false
        }
        if (!isParticipant) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant in this call")
        }
        val participant = participants.findByAccountTypeAndAccountId(normalizedType, accountId).orElse(null)
        if (participant != null && participant.callId != callId && !isTerminal(call.status)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "You are already in another active call")
        }
        return call
    }

    private fun requireCallee(call: VoiceCallEntity, user: UserEntity) {
        if (call.calleeUserId != requireNotNull(user.id)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the customer can answer or decline this call")
        }
    }

    fun otherParticipant(callId: String, accountType: String, accountId: Long): CallAccountRef =
        otherParticipant(
            calls.findByCallId(callId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Call not found")
            },
            accountType,
            accountId
        )

    private fun otherParticipant(call: VoiceCallEntity, accountType: String, accountId: Long): CallAccountRef =
        when (accountType.uppercase()) {
            "EMPLOYEE" -> if (call.callerEmployeeId == accountId) CallAccountRef("USER", call.calleeUserId)
            else throw ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant in this call")
            "USER" -> if (call.calleeUserId == accountId) CallAccountRef("EMPLOYEE", call.callerEmployeeId)
            else throw ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant in this call")
            else -> throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid account type")
        }

    fun socketAuthorized(accountType: String, accountId: Long, callId: String): Boolean =
        calls.findByCallId(callId)
            .map { call ->
                val participant = when (accountType.uppercase()) {
                    "EMPLOYEE" -> call.callerEmployeeId == accountId
                    else -> call.calleeUserId == accountId
                }
                val active = when (accountType.uppercase()) {
                    "EMPLOYEE" -> employees.findById(accountId).map { it.active }.orElse(false)
                    else -> users.findById(accountId).map { it.active && it.deletedAt == null }.orElse(false)
                }
                call.status in setOf(ACCEPTED, CONNECTED) && participant && active
            }
            .orElse(false)

    private fun broadcastStatus(call: VoiceCallEntity) {
        val connectedAtEpochMillis = call.connectedAt?.toEpochMilli()?.toString() ?: "null"
        val endedAtEpochMillis = call.endedAt?.toEpochMilli()?.toString() ?: "null"
        val payload = """{"type":"status","callId":"${call.callId}","status":"${call.status}","connectedAtEpochMillis":$connectedAtEpochMillis,"endedAtEpochMillis":$endedAtEpochMillis}"""
        websocket.sendToCallUser(call.callId, "EMPLOYEE", call.callerEmployeeId, payload)
        websocket.sendToCallUser(call.callId, "USER", call.calleeUserId, payload)
        if (isTerminal(call.status)) {
            websocket.closeCall(call.callId)
            websocket.clearCall(call.callId)
        }
    }

    private fun response(call: VoiceCallEntity): VoiceCallResponse {
        val caller = employees.findById(call.callerEmployeeId).orElse(null)
        val callee = users.findById(call.calleeUserId).orElse(null)
        return VoiceCallResponse(
            callId = call.callId,
            status = call.status,
            callerName = caller?.name,
            callerPublicId = caller?.publicId ?: "",
            calleeName = callee?.name,
            calleePublicId = callee?.publicId ?: "",
            createdAt = call.createdAt.toString(),
            ringingExpiresAt = call.ringingExpiresAt.toString(),
            acceptedAt = call.acceptedAt?.toString(),
            connectedAt = call.connectedAt?.toString(),
            endedAt = call.endedAt?.toString(),
            endedReason = call.endedReason,
            iceServers = iceServers()
        )
    }

    private fun iceServers(): List<CallIceServerResponse> {
        val urls = properties.iceServers
            .split(',', ' ', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (urls.isEmpty()) return emptyList()

        val hasTurn = urls.any { it.startsWith("turn:", true) || it.startsWith("turns:", true) }
        return listOf(
            CallIceServerResponse(
                urls = urls,
                username = properties.turnUsername.takeIf { hasTurn && it.isNotBlank() },
                credential = properties.turnCredential.takeIf { hasTurn && it.isNotBlank() }
            )
        )
    }
}
