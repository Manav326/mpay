package com.recharge.backend.service

import com.recharge.backend.api.ForgotPasswordResponse
import com.recharge.backend.api.ResetPasswordRequest
import com.recharge.backend.domain.PasswordResetOtpEntity
import com.recharge.backend.repository.PasswordResetOtpRepository
import com.recharge.backend.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

@Service
class PasswordResetService(
    private val users: UserRepository,
    private val resetOtps: PasswordResetOtpRepository,
    private val passwordEncoder: PasswordEncoder,
    private val twilioVerifyClient: TwilioVerifyClient,
    @Value("\${app.auth.password-reset.otp-ttl-seconds:600}") private val otpTtlSeconds: Long,
    @Value("\${app.auth.password-reset.provider:mock}") private val provider: String
) {
    companion object { private const val MAX_ATTEMPTS = 5 }

    @Transactional
    fun requestOtp(mobileInput: String): ForgotPasswordResponse {
        val mobile = normalizeMobile(mobileInput)
        val user = users.findByMobile(mobile).orElse(null)
        val expiresIn = otpTtlSeconds.coerceIn(60L, 600L)
        if (user == null || !user.active) {
            return ForgotPasswordResponse("OTP_SENT", expiresIn, null, provider.trim().lowercase())
        }

        val now = Instant.now()
        val entity = resetOtps.findByMobile(mobile).orElseGet { PasswordResetOtpEntity(mobile = mobile) }
        entity.attempts = 0
        entity.usedAt = null
        entity.updatedAt = now

        return when (provider.trim().lowercase()) {
            "mock" -> {
                val otp = generateMockOtp()
                entity.otpHash = passwordEncoder.encode(otp)
                entity.verificationSid = null
                entity.expiresAt = now.plusSeconds(expiresIn)
                resetOtps.save(entity)
                ForgotPasswordResponse("OTP_SENT", expiresIn, otp, "mock")
            }
            "twilio" -> {
                val twilio = twilioVerifyClient.sendSms(mobile, expiresIn)
                entity.otpHash = null
                entity.verificationSid = twilio.verificationSid
                entity.expiresAt = now.plusSeconds(twilio.expiresInSeconds)
                resetOtps.save(entity)
                ForgotPasswordResponse("OTP_SENT", twilio.expiresInSeconds, null, "twilio")
            }
            else -> throw IllegalStateException("Unsupported password reset OTP provider: $provider. Use 'mock' or 'twilio'.")
        }
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest) {
        val mobile = normalizeMobile(request.mobile)
        val user = users.findByMobile(mobile).orElseThrow { IllegalArgumentException("Invalid OTP or reset request") }
        if (!user.active) throw IllegalStateException("User account is inactive")
        val reset = resetOtps.findByMobile(mobile).orElseThrow { IllegalArgumentException("Invalid OTP or reset request") }
        val now = Instant.now()
        if (reset.usedAt != null || reset.expiresAt.isBefore(now)) throw IllegalArgumentException("OTP is expired or already used")
        if (reset.attempts >= MAX_ATTEMPTS) throw IllegalArgumentException("Too many invalid OTP attempts. Request a new OTP.")

        when (provider.trim().lowercase()) {
            "mock" -> {
                if (reset.otpHash.isNullOrBlank() || !passwordEncoder.matches(request.otp, reset.otpHash)) {
                    reset.attempts += 1; reset.updatedAt = now; resetOtps.save(reset); throw IllegalArgumentException("Invalid OTP")
                }
            }
            "twilio" -> {
                if (reset.verificationSid.isNullOrBlank()) throw IllegalArgumentException("Invalid OTP or reset request")
                val result = twilioVerifyClient.checkSms(mobile, request.otp)
                if (!result.status.equals("approved", ignoreCase = true)) {
                    reset.attempts += 1; reset.updatedAt = now; resetOtps.save(reset); throw IllegalArgumentException("Invalid OTP")
                }
            }
            else -> throw IllegalStateException("Unsupported password reset OTP provider: $provider. Use 'mock' or 'twilio'.")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
        users.save(user)
        reset.usedAt = now
        reset.updatedAt = now
        resetOtps.save(reset)
    }

    private fun generateMockOtp(): String = ThreadLocalRandom.current().nextInt(100000, 1000000).toString()
    private fun normalizeMobile(mobile: String): String = mobile.trim()
}
