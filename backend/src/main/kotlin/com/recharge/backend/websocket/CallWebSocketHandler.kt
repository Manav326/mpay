package com.recharge.backend.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.recharge.backend.repository.UserRepository
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
    private val users: UserRepository
) : WebSocketHandler {
    private val userBySession = ConcurrentHashMap<String, Long>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.attributes["userId"] as Long
        registry.register(userId, session)
        userBySession[session.id] = userId
    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        val textMessage = message as? TextMessage ?: return
        val userId = userBySession[session.id] ?: return
        val callId = session.attributes["callId"]?.toString() ?: return
        if (!calls.socketAuthorized(userId, callId)) return

        val node = runCatching { objectMapper.readTree(textMessage.payload) }.getOrNull() ?: return
        val messageCallId = node.get("callId")?.asText()
        if (!messageCallId.isNullOrBlank() && messageCallId != callId) return

        when (node.get("type")?.asText()) {
            "ready", "signal" -> {
                val otherUserId = calls.otherParticipant(callId, userId)
                registry.sendToUser(otherUserId, message.payload)
            }
            "connected" -> calls.markConnected(userId, callId)
            "hangup" -> {
                val user = users.findById(userId).orElse(null) ?: return
                calls.end(user, callId)
            }
        }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) = Unit

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        val userId = userBySession.remove(session.id) ?: return
        registry.unregister(userId, session)
    }

    override fun supportsPartialMessages(): Boolean = false
}
