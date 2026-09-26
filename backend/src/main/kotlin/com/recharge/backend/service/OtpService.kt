package com.recharge.backend.service

import com.recharge.backend.api.OtpSendResponse
import com.recharge.backend.api.OtpVerifyResponse
import com.recharge.backend.domain.PasswordResetOtpEntity
import com.recharge.backend.repository.PasswordResetOtpRepository
import com.recharge.backend.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

class OtpRateLimitException(
    val retryAfterSeconds: Long,
    message: String
) : RuntimeException(message)

@Service
class OtpService(
    private val users: UserRepository,
    private val otpRepository: PasswordResetOtpRepository,
    private val passwordEncoder: PasswordEncoder,
    private val providers: List<OtpDeliveryProvider>,
    @Value("\${app.auth.otp.provider:way2api}") private val providerName: String,
    @Value("\${app.auth.otp.ttl-seconds:600}") private val configuredTtlSeconds: Long,
    @Value("\${app.auth.otp.resend-cooldown-seconds:60}") private val resendCooldownSeconds: Long,
    @Value("\${app.auth.otp.max-sends-per-hour:5}") private val maxSendsPerHour: Int,
    @Value("\${app.auth.otp.verification-token-ttl-seconds:600}") private val verificationTokenTtlSeconds: Long
) {
    companion object {
        private const val MAX_VERIFY_ATTEMPTS = 5
        private const val OTP_LENGTH = 6
        private val RANDOM = SecureRandom()
    }

    private val ttlSeconds get() = configuredTtlSeconds.coerceIn(60L, 600L)
    private val tokenTtlSeconds get() = verificationTokenTtlSeconds.coerceIn(60L, 900L)

    @Transactional
    fun send(
        mobileInput: String,
        purpose: OtpPurpose,
        allowUnknownUser: Boolean
    ): OtpSendResponse {
        val mobile = normalizeMobile(mobileInput)
        val user = users.findByMobile(mobile).orElse(null)

        when (purpose) {
            OtpPurpose.PASSWORD_RESET -> {
                if (!allowUnknownUser && (user == null || !user.active)) {
                    return OtpSendResponse(
                        status = "OTP_SENT",
                        purpose = purpose.name,
                        expiresInSeconds = ttlSeconds,
                        resendAfterSeconds = resendCooldownSeconds,
                        maskedMobile = maskMobile(mobile),
                        deliveryMode = providerName.trim().lowercase()
                    )
                }
            }
            OtpPurpose.REGISTRATION -> {
                if (user != null) {
                    throw IllegalArgumentException("A user with this mobile number already exists")
                }
            }
        }

        val now = Instant.now()
        val record = otpRepository.findByMobileAndPurpose(mobile, purpose.name)
            .orElseGet {
                PasswordResetOtpEntity(
                    mobile = mobile,
                    purpose = purpose.name
                )
            }

        enforceSendLimits(record, now)

        val otp = generateOtp()
        val provider = providers.firstOrNull { it.providerName.equals(providerName.trim(), ignoreCase = true) }
            ?: throw IllegalStateException("Unsupported OTP provider: $providerName")

        val delivery = provider.send(mobile, otp, purpose)

        record.otpHash = passwordEncoder.encode(otp)
        record.expiresAt = now.plusSeconds(ttlSeconds)
        record.attempts = 0
        record.usedAt = null
        record.verifiedAt = null
        record.verificationTokenHash = null
        record.verificationTokenExpiresAt = null
        record.providerOrderId = delivery.providerOrderId
        record.lastSentAt = now
        if (record.sendWindowStartedAt == null || record.sendWindowStartedAt!!.plus(Duration.ofHours(1)).isBefore(now)) {
            record.sendWindowStartedAt = now
            record.sendCount = 1
        } else {
            record.sendCount += 1
        }
        record.updatedAt = now
        otpRepository.save(record)

        return OtpSendResponse(
            status = "OTP_SENT",
            purpose = purpose.name,
            expiresInSeconds = ttlSeconds,
            resendAfterSeconds = resendCooldownSeconds,
            maskedMobile = maskMobile(mobile),
            deliveryMode = delivery.provider,
            demoOtp = delivery.demoOtp
        )
    }

    @Transactional
    fun verify(
        mobileInput: String,
        purpose: OtpPurpose,
        otp: String
    ): OtpVerifyResponse {
        val mobile = normalizeMobile(mobileInput)
        val record = otpRepository.findByMobileAndPurpose(mobile, purpose.name)
            .orElseThrow { IllegalArgumentException("Invalid OTP or verification request") }

        val now = Instant.now()
        if (record.usedAt != null || record.expiresAt.isBefore(now)) {
            throw IllegalArgumentException("OTP is expired or already used")
        }
        if (record.attempts >= MAX_VERIFY_ATTEMPTS) {
            throw IllegalArgumentException("Too many invalid OTP attempts. Request a new OTP.")
        }
        if (otp.length != OTP_LENGTH || !otp.all(Char::isDigit)) {
            throw IllegalArgumentException("OTP must be a 6 digit number")
        }

        if (record.otpHash.isNullOrBlank() || !passwordEncoder.matches(otp, record.otpHash)) {
            record.attempts += 1
            record.updatedAt = now
            otpRepository.save(record)
            throw IllegalArgumentException("Invalid OTP")
        }

        record.usedAt = now
        record.verifiedAt = now
        record.attempts = 0

        val verificationToken = if (purpose == OtpPurpose.REGISTRATION) {
            generateVerificationToken().also { token ->
                record.verificationTokenHash = sha256(token)
                record.verificationTokenExpiresAt = now.plusSeconds(tokenTtlSeconds)
            }
        } else {
            null
        }

        record.otpHash = null
        record.updatedAt = now
        otpRepository.save(record)

        return OtpVerifyResponse(
            verified = true,
            purpose = purpose.name,
            verificationToken = verificationToken,
            verificationTokenExpiresInSeconds = if (verificationToken != null) tokenTtlSeconds else null
        )
    }

    @Transactional
    fun consumeRegistrationVerification(mobileInput: String, verificationToken: String) {
        val mobile = normalizeMobile(mobileInput)
        val record = otpRepository.findByMobileAndPurpose(mobile, OtpPurpose.REGISTRATION.name)
            .orElseThrow { IllegalArgumentException("Mobile number verification is required or has expired") }

        val now = Instant.now()
        if (record.verifiedAt == null ||
            record.verificationTokenHash.isNullOrBlank() ||
            record.verificationTokenExpiresAt == null ||
            record.verificationTokenExpiresAt!!.isBefore(now) ||
            !constantTimeEquals(record.verificationTokenHash!!, sha256(verificationToken))
        ) {
            throw IllegalArgumentException("Mobile number verification is required or has expired")
        }

        record.verificationTokenHash = null
        record.verificationTokenExpiresAt = null
        record.updatedAt = now
        otpRepository.save(record)
    }

    private fun enforceSendLimits(record: PasswordResetOtpEntity, now: Instant) {
        val lastSentAt = record.lastSentAt
        if (lastSentAt != null) {
            val elapsed = Duration.between(lastSentAt, now).seconds
            val remaining = resendCooldownSeconds - elapsed
            if (remaining > 0) {
                throw OtpRateLimitException(
                    retryAfterSeconds = remaining,
                    message = "Please wait $remaining seconds before requesting another OTP."
                )
            }
        }

        val windowStart = record.sendWindowStartedAt
        if (windowStart != null && windowStart.plus(Duration.ofHours(1)).isAfter(now) && record.sendCount >= maxSendsPerHour) {
            val retryAfter = Duration.between(now, windowStart.plus(Duration.ofHours(1))).seconds.coerceAtLeast(1)
            throw OtpRateLimitException(
                retryAfterSeconds = retryAfter,
                message = "Too many OTP requests. Please try again later."
            )
        }
    }

    private fun normalizeMobile(value: String): String {
        val mobile = value.trim()
        require(Regex("[6-9][0-9]{9}").matches(mobile)) {
            "Mobile number must be a valid 10 digit Indian mobile number"
        }
        return mobile
    }

    private fun maskMobile(mobile: String): String = "******${mobile.takeLast(4)}"

    private fun generateOtp(): String =
        (100000 + RANDOM.nextInt(900000)).toString()

    private fun generateVerificationToken(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(RANDOM::nextBytes))

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(
            left.toByteArray(StandardCharsets.UTF_8),
            right.toByteArray(StandardCharsets.UTF_8)
        )
}
