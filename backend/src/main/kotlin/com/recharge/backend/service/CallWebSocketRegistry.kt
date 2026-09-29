package com.recharge.backend.service

import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Component
class CallWebSocketRegistry {
    private data class ParticipantKey(val callId: String, val userId: Long)

    private val sessionsByUser = ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>>()
    private val sessionsByCallUser = ConcurrentHashMap<ParticipantKey, CopyOnWriteArraySet<WebSocketSession>>()
    private val readyUsers = ConcurrentHashMap<String, CopyOnWriteArraySet<Long>>()
    private val disconnectedSince = ConcurrentHashMap<ParticipantKey, Instant>()

    fun register(userId: Long, callId: String, session: WebSocketSession) {
        sessionsByUser.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
        val key = ParticipantKey(callId, userId)
        sessionsByCallUser.computeIfAbsent(key) { CopyOnWriteArraySet() }.add(session)
        disconnectedSince.remove(key)
    }

    fun unregister(userId: Long, callId: String, session: WebSocketSession) {
        sessionsByUser[userId]?.let { sessions ->
            sessions.remove(session)
            if (sessions.isEmpty()) sessionsByUser.remove(userId, sessions)
        }

        val key = ParticipantKey(callId, userId)
        sessionsByCallUser[key]?.let { sessions ->
            sessions.remove(session)
            if (sessions.isEmpty()) {
                sessionsByCallUser.remove(key, sessions)
                disconnectedSince.putIfAbsent(key, Instant.now())
            }
        }
    }

    fun sendToCallUser(
        callId: String,
        userId: Long,
        payload: String,
        exceptSession: WebSocketSession? = null
    ) {
        sessionsByCallUser[ParticipantKey(callId, userId)]?.forEach { session ->
            if (session !== exceptSession && session.isOpen) {
                runCatching { session.sendMessage(TextMessage(payload)) }
            }
        }
    }

    fun hasOpenSession(callId: String, userId: Long): Boolean =
        sessionsByCallUser[ParticipantKey(callId, userId)]?.any { it.isOpen } == true

    fun disconnectedSince(callId: String, userId: Long): Instant? =
        disconnectedSince[ParticipantKey(callId, userId)]

    fun disconnectedSinceOrMarkNow(callId: String, userId: Long): Instant =
        disconnectedSince.computeIfAbsent(ParticipantKey(callId, userId)) { Instant.now() }

    fun markReady(callId: String, userId: Long): Boolean =
        readyUsers.computeIfAbsent(callId) { CopyOnWriteArraySet() }.add(userId)

    fun isReady(callId: String, userId: Long): Boolean =
        readyUsers[callId]?.contains(userId) == true

    fun clearReady(callId: String, userId: Long) {
        readyUsers[callId]?.remove(userId)
        if (readyUsers[callId]?.isEmpty() == true) readyUsers.remove(callId)
    }

    fun clearCall(callId: String) {
        readyUsers.remove(callId)
        disconnectedSince.keys.removeIf { it.callId == callId }
    }

    fun closeCall(callId: String) {
        sessionsByCallUser.entries
            .filter { it.key.callId == callId }
            .flatMap { it.value.toList() }
            .distinctBy { it.id }
            .forEach { session ->
                runCatching { session.close() }
            }
    }
}
