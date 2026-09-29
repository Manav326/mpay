package com.recharge.backend.service

import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Component
class CallWebSocketRegistry {
    private val sessions = ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>>()

    fun register(userId: Long, session: WebSocketSession) {
        sessions.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
    }

    fun unregister(userId: Long, session: WebSocketSession) {
        sessions[userId]?.remove(session)
        if (sessions[userId]?.isEmpty() == true) sessions.remove(userId)
    }

    fun sendToUser(userId: Long, payload: String, exceptSession: WebSocketSession? = null) {
        sessions[userId]?.forEach { session ->
            if (session !== exceptSession && session.isOpen) {
                runCatching { session.sendMessage(TextMessage(payload)) }
            }
        }
    }

    fun hasOpenSession(userId: Long): Boolean =
        sessions[userId]?.any { it.isOpen } == true


    fun sendToUsers(userIds: Collection<Long>, payload: String, exceptSession: WebSocketSession? = null) {
        userIds.distinct().forEach { sendToUser(it, payload, exceptSession) }
    }
}
