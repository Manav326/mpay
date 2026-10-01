package com.recharge.backend.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidFcmOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.recharge.backend.config.CallProperties
import com.recharge.backend.domain.CallPushDeviceEntity
import com.recharge.backend.repository.CallPushDeviceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import java.util.Base64

@Service
class CallPushService(
    private val devices: CallPushDeviceRepository,
    private val properties: CallProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // FCM recommends treating registrations as stale after roughly a month without a client connection.
        // mPay gives active logged-in devices a small grace period while the app refreshes the token on resume.
        private val DEVICE_STALE_AFTER: Duration = Duration.ofDays(35)
    }

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

    fun hasActiveDevice(userId: Long): Boolean {
        val cutoff = Instant.now().minus(DEVICE_STALE_AFTER)
        val activeDevices = devices.findAllByUserIdAndActiveTrue(userId)
        return activeDevices.any { it.lastSeenAt.isAfter(cutoff) }
    }

    private fun deactivateInvalidToken(token: String) {
        devices.findByToken(token).orElse(null)?.let { device ->
            if (device.active) {
                device.active = false
                device.updatedAt = Instant.now()
                devices.save(device)
            }
        }
    }

    fun revoke(userId: Long, token: String) {
        val normalized = token.trim()
        if (normalized.isBlank()) return

        val existing = devices.findByToken(normalized).orElse(null) ?: return
        if (existing.userId != userId) return

        existing.active = false
        existing.updatedAt = Instant.now()
        devices.save(existing)
    }

    private fun send(userId: Long, data: Map<String, String>) {
        val tokens = devices.findAllByUserIdAndActiveTrue(userId).map { it.token }.distinct()
        if (tokens.isEmpty()) {
            log.warn(
                "FCM voice-call send skipped: no active device tokens. event={} callId={} userId={}",
                data["event"],
                data["callId"],
                userId
            )
            return
        }

        val messaging = firebaseMessaging() ?: return
        // Incoming calls use a high-priority notification + data message.
        // Foreground delivery still reaches MpayFirebaseMessagingService so the app can
        // render its full CallStyle/ringtone path. In the background/locked state, FCM/Android
        // can independently place the audible call notification in the system tray even when
        // the app process is not running.
        val androidConfigBuilder = AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .setTtl(properties.ringingTimeoutSeconds.coerceAtLeast(10) * 1000L)
            .setFcmOptions(AndroidFcmOptions.withAnalyticsLabel("voice-call"))

        if (data["event"] == "CALL_INCOMING") {
            androidConfigBuilder.setNotification(
                com.google.firebase.messaging.AndroidNotification.builder()
                    .setTitle("Incoming mPay call")
                    .setBody(data["callerName"] ?: "mPay Support")
                    .setChannelId("incoming_calls_v5")
                    .setSound("default")
                    .setPriority(com.google.firebase.messaging.AndroidNotification.Priority.HIGH)
                    .build()
            )
        }

        val androidConfig = androidConfigBuilder.build()

        try {
            val response = messaging.sendEachForMulticast(
                MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setAndroidConfig(androidConfig)
                    .putAllData(data)
                    .build()
            )

            var successCount = 0
            var failureCount = 0
            response.responses.forEachIndexed { index, result ->
                if (result.isSuccessful) {
                    successCount++
                } else {
                    failureCount++
                    val exception = result.exception
                    if (
                        exception is FirebaseMessagingException &&
                        exception.messagingErrorCode == MessagingErrorCode.UNREGISTERED
                    ) {
                        deactivateInvalidToken(tokens[index])
                        log.info(
                            "Deactivated invalid FCM voice-call token. event={} callId={} tokenIndex={}",
                            data["event"],
                            data["callId"],
                            index
                        )
                    }
                    log.warn(
                        "FCM voice-call delivery failed. event={} callId={} tokenIndex={} error={}",
                        data["event"],
                        data["callId"],
                        index,
                        exception?.message
                    )
                }
            }
            log.info(
                "FCM voice-call send result. event={} callId={} userId={} tokenCount={} successCount={} failureCount={} analyticsLabel=voice-call",
                data["event"],
                data["callId"],
                userId,
                tokens.size,
                successCount,
                failureCount
            )
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
                    val encoded = properties.firebaseServiceAccountJsonBase64.trim()
                    if (encoded.isBlank()) {
                        log.warn(
                            "Firebase Admin service account is not configured; " +
                                "voice-call push delivery is disabled."
                        )
                        return@synchronized
                    }

                    val jsonBytes = Base64.getDecoder().decode(encoded)
                    val credentials = GoogleCredentials.fromStream(ByteArrayInputStream(jsonBytes))
                    FirebaseApp.initializeApp(
                        FirebaseOptions.builder()
                            .setCredentials(credentials)
                            .build()
                    )
                }
            }
            FirebaseMessaging.getInstance()
        } catch (ex: Exception) {
            log.warn(
                "Unable to initialize Firebase Admin for voice-call push delivery: {}",
                ex.message
            )
            null
        }
    }
}
