package com.recharge.backend.service

import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Component
class CallWebSocketRegistry {
    private data class ParticipantKey(val callId: String, val accountType: String, val accountId: Long)
    private data class AccountKey(val accountType: String, val accountId: Long)

    private val sessionsByAccount = ConcurrentHashMap<AccountKey, CopyOnWriteArraySet<WebSocketSession>>()
    private val sessionsByCallAccount = ConcurrentHashMap<ParticipantKey, CopyOnWriteArraySet<WebSocketSession>>()
    private val readyAccounts = ConcurrentHashMap<String, CopyOnWriteArraySet<AccountKey>>()
    private val disconnectedSince = ConcurrentHashMap<ParticipantKey, Instant>()

    fun register(accountType: String, accountId: Long, callId: String, session: WebSocketSession) {
        val normalized = accountType.uppercase()
        val accountKey = AccountKey(normalized, accountId)
        sessionsByAccount.computeIfAbsent(accountKey) { CopyOnWriteArraySet() }.add(session)
        val key = ParticipantKey(callId, normalized, accountId)
        sessionsByCallAccount.computeIfAbsent(key) { CopyOnWriteArraySet() }.add(session)
        disconnectedSince.remove(key)
    }

    fun unregister(accountType: String, accountId: Long, callId: String, session: WebSocketSession) {
        val normalized = accountType.uppercase()
        val accountKey = AccountKey(normalized, accountId)
        sessionsByAccount[accountKey]?.let { sessions ->
            sessions.remove(session)
            if (sessions.isEmpty()) sessionsByAccount.remove(accountKey, sessions)
        }
        val key = ParticipantKey(callId, normalized, accountId)
        sessionsByCallAccount[key]?.let { sessions ->
            sessions.remove(session)
            if (sessions.isEmpty()) {
                sessionsByCallAccount.remove(key, sessions)
                disconnectedSince.putIfAbsent(key, Instant.now())
            }
        }
    }

    fun sendToCallUser(callId: String, accountType: String, accountId: Long, payload: String, exceptSession: WebSocketSession? = null) {
        sessionsByCallAccount[ParticipantKey(callId, accountType.uppercase(), accountId)]?.forEach { session ->
            if (session !== exceptSession && session.isOpen) runCatching { session.sendMessage(TextMessage(payload)) }
        }
    }

    fun hasOpenSession(callId: String, accountType: String, accountId: Long): Boolean =
        sessionsByCallAccount[ParticipantKey(callId, accountType.uppercase(), accountId)]?.any { it.isOpen } == true

    fun disconnectedSince(callId: String, accountType: String, accountId: Long): Instant? =
        disconnectedSince[ParticipantKey(callId, accountType.uppercase(), accountId)]

    fun disconnectedSinceOrMarkNow(callId: String, accountType: String, accountId: Long): Instant =
        disconnectedSince.computeIfAbsent(ParticipantKey(callId, accountType.uppercase(), accountId)) { Instant.now() }

    fun markReady(callId: String, accountType: String, accountId: Long): Boolean =
        readyAccounts.computeIfAbsent(callId) { CopyOnWriteArraySet() }
            .add(AccountKey(accountType.uppercase(), accountId))

    fun isReady(callId: String, accountType: String, accountId: Long): Boolean =
        readyAccounts[callId]?.contains(AccountKey(accountType.uppercase(), accountId)) == true

    fun clearReady(callId: String, accountType: String, accountId: Long) {
        readyAccounts[callId]?.remove(AccountKey(accountType.uppercase(), accountId))
        if (readyAccounts[callId]?.isEmpty() == true) readyAccounts.remove(callId)
    }

    fun clearCall(callId: String) {
        readyAccounts.remove(callId)
        disconnectedSince.keys.removeIf { it.callId == callId }
    }

    fun closeCall(callId: String) {
        val matching = sessionsByCallAccount.entries.filter { it.key.callId == callId }.toList()
        matching.forEach { (key, sessions) ->
            sessionsByCallAccount.remove(key, sessions)
            disconnectedSince.remove(key)
        }
        matching.flatMap { it.value.toList() }.distinctBy { it.id }.forEach { session ->
            runCatching { session.close() }
        }
    }
}