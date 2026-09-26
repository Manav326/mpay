package com.recharge.backend.service

import com.recharge.backend.api.ForgotPasswordResponse
import com.recharge.backend.api.ResetPasswordRequest
import com.recharge.backend.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class PasswordResetService(
    private val users: UserRepository,
    private val otpService: OtpService
) {
    @Transactional
    fun requestOtp(mobileInput: String): ForgotPasswordResponse {
        val response = otpService.send(
            mobileInput = mobileInput,
            purpose = OtpPurpose.PASSWORD_RESET,
            allowUnknownUser = false
        )
        return ForgotPasswordResponse(
            status = response.status,
            expiresInSeconds = response.expiresInSeconds,
            demoOtp = response.demoOtp,
            deliveryMode = response.deliveryMode,
            resendAfterSeconds = response.resendAfterSeconds
        )
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest) {
        val mobile = request.mobile.trim()
        val user = users.findByMobile(mobile).orElseThrow {
            IllegalArgumentException("Invalid OTP or reset request")
        }
        if (!user.active) throw IllegalStateException("User account is inactive")

        otpService.verify(
            mobileInput = mobile,
            purpose = OtpPurpose.PASSWORD_RESET,
            otp = request.otp
        )

        user.passwordHash = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(request.newPassword)
        users.save(user)
    }
}
