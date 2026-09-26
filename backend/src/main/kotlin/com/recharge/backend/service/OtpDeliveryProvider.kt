package com.recharge.backend.service

enum class OtpPurpose {
    PASSWORD_RESET,
    REGISTRATION;

    companion object {
        fun parse(value: String): OtpPurpose = when (value.trim().uppercase()) {
            "PASSWORD_RESET", "PASSWORD-RESET" -> PASSWORD_RESET
            "REGISTRATION", "REGISTER", "SIGNUP", "SIGN_UP" -> REGISTRATION
            else -> throw IllegalArgumentException("Unsupported OTP purpose")
        }
    }
}

data class OtpDeliveryResult(
    val provider: String,
    val providerOrderId: String? = null,
    val demoOtp: String? = null
)

interface OtpDeliveryProvider {
    val providerName: String
    fun send(mobile: String, otp: String, purpose: OtpPurpose): OtpDeliveryResult
}
