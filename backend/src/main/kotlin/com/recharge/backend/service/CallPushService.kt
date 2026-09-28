package com.recharge.backend.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.recharge.backend.config.CallProperties
import com.recharge.backend.domain.CallPushDeviceEntity
import com.recharge.backend.repository.CallPushDeviceRepository
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.util.Base64
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CallPushService(
    private val devices: CallPushDeviceRepository,
    private val properties: CallProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun register(userId: Long, token: String, platform: String) {
        val normalized = token.trim()
        if (normalized.isBlank()) return

        val now = Instant.now()
        val existing = devices.findByToken(normalized).orElse(null)
        if (existing == null) {
            devices.save(
                CallPushDeviceEntity(
                    userId = userId,
                    token = normalized,
                    platform = platform.trim().uppercase().ifBlank { "ANDROID" },
                    active = true,
                    lastSeenAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            existing.userId = userId
            existing.platform = platform.trim().uppercase().ifBlank { "ANDROID" }
            existing.active = true
            existing.lastSeenAt = now
            existing.updatedAt = now
            devices.save(existing)
        }
    }

    fun sendIncomingCall(userId: Long, callId: String, callerName: String?, expiresAt: Instant) {
        send(
            userId = userId,
            data = mapOf(
                "event" to "CALL_INCOMING",
                "callId" to callId,
                "callerName" to (callerName ?: "mPay Support"),
                "expiresAt" to expiresAt.toString()
            )
        )
    }

    fun sendCallEnded(userId: Long, callId: String, status: String) {
        send(
            userId = userId,
            data = mapOf(
                "event" to "CALL_ENDED",
                "callId" to callId,
                "status" to status
            )
        )
    }

    private fun send(userId: Long, data: Map<String, String>) {
        val tokens = devices.findAllByUserIdAndActiveTrue(userId).map { it.token }.distinct()
        if (tokens.isEmpty()) return

        val messaging = firebaseMessaging() ?: return
        val androidConfig = AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .setTtl(properties.ringingTimeoutSeconds.coerceAtLeast(10) * 1000)
            .build()

        try {
            val response = messaging.sendEachForMulticast(
                MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setAndroidConfig(androidConfig)
                    .putAllData(data)
                    .build()
            )

            response.responses.forEachIndexed { index, result ->
                if (!result.isSuccessful) {
                    log.debug("FCM delivery failed for call device token {}: {}", index, result.exception?.message)
                }
            }
        } catch (ex: Exception) {
            // Push delivery is an enhancement to the call flow. A transient FCM problem must
            // never turn an already-authorized call creation into a 500 response.
            log.warn("Unable to send mPay voice-call push notification: {}", ex.message)
        }
    }

    private fun firebaseMessaging(): FirebaseMessaging? {
        return try {
            synchronized(FirebaseApp::class.java) {
                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp()
                }
            }
            FirebaseMessaging.getInstance()
        } catch (ex: Exception) {
            log.warn(
                "Firebase Admin is not configured; voice-call push delivery is disabled. " +
                    "Configure application-default credentials before production use. Cause: {}",
                ex.message
            )
            null
        }
    }
}
