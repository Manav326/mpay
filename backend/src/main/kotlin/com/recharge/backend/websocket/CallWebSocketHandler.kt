package com.recharge.backend.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.repository.EmployeeRepository
import com.recharge.backend.service.CallWebSocketRegistry
import com.recharge.backend.service.VoiceCallService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Component
class CallWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val registry: CallWebSocketRegistry,
    private val calls: VoiceCallService,
    private val users: UserRepository,
    private val employees: EmployeeRepository
) : WebSocketHandler {
    private val accountBySession = ConcurrentHashMap<String, Long>()
    private val accountTypeBySession = ConcurrentHashMap<String, String>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val accountId = session.attributes["accountId"] as Long
        val accountType = session.attributes["accountType"]?.toString() ?: "USER"
        val callId = session.attributes["callId"]?.toString()
        registry.register(accountId, callId.orEmpty(), session)
        accountBySession[session.id] = accountId
        accountTypeBySession[session.id] = accountType

        if (!callId.isNullOrBlank()) {
            registry.markReady(callId, accountId)
            val otherAccountId = runCatching { calls.otherParticipant(callId, accountId) }.getOrNull()
            if (otherAccountId != null &&
                registry.hasOpenSession(callId, otherAccountId) &&
                registry.isReady(callId, otherAccountId)
            ) {
                registry.sendToCallUser(callId, accountId, """{"type":"ready","callId":"$callId"}""")
                registry.sendToCallUser(callId, otherAccountId, """{"type":"ready","callId":"$callId"}""")
            }
        }
    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        val textMessage = message as? TextMessage ?: return
        if (textMessage.payload.length > 64 * 1024) return

        val accountId = accountBySession[session.id] ?: return
        val accountType = accountTypeBySession[session.id] ?: "USER"
        val callId = session.attributes["callId"]?.toString() ?: return
        if (!calls.socketAuthorized(accountType, accountId, callId)) return

        val node = runCatching { objectMapper.readTree(textMessage.payload) }.getOrNull() ?: return
        if (node.get("callId")?.asText() != callId) return

        when (node.get("type")?.asText()) {
            "ready" -> {
                val otherAccountId = calls.otherParticipant(callId, accountId)
                registry.markReady(callId, accountId)
                if (registry.hasOpenSession(callId, otherAccountId) && registry.isReady(callId, otherAccountId)) {
                    registry.sendToCallUser(callId, accountId, """{"type":"ready","callId":"$callId"}""")
                    registry.sendToCallUser(callId, otherAccountId, """{"type":"ready","callId":"$callId"}""")
                }
            }
            "signal" -> {
                val payload = node.get("payload")
                val kind = payload?.get("kind")?.asText()
                if (payload == null || !payload.isObject || kind !in setOf("offer", "answer", "candidate")) return
                val otherAccountId = calls.otherParticipant(callId, accountId)
                registry.sendToCallUser(callId, otherAccountId, message.payload)
            }
            "connected" -> calls.markConnected(accountId, callId)
            "hangup" -> {
                if (accountType == "EMPLOYEE") {
                    val employee = employees.findById(accountId).orElse(null) ?: return
                    calls.end(employee, callId)
                } else {
                    val user = users.findById(accountId).orElse(null) ?: return
                    calls.end(user, callId)
                }
            }
        }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) = Unit

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        val accountId = accountBySession.remove(session.id) ?: return
        accountTypeBySession.remove(session.id)
        val callId = session.attributes["callId"]?.toString()
        if (!callId.isNullOrBlank()) {
            registry.unregister(accountId, callId, session)
            if (!registry.hasOpenSession(callId, accountId)) registry.clearReady(callId, accountId)
        } else {
            registry.unregister(accountId, "", session)
        }
    }

    override fun supportsPartialMessages(): Boolean = false
}
