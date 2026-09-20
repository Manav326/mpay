package com.recharge.backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

class OtpDeliveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Component
class TwilioVerifyClient(
    @Value("\${app.auth.password-reset.twilio.enabled:true}") private val enabled: Boolean,
    @Value("\${app.twilio.account-sid:}") private val accountSid: String,
    @Value("\${app.twilio.auth-token:}") private val authToken: String,
    @Value("\${app.twilio.verify-service-sid:}") private val verifyServiceSid: String
) {
    private val restClient: RestClient by lazy {
        RestClient.builder().baseUrl("https://verify.twilio.com/v2")
            .defaultHeaders { headers -> headers.setBasicAuth(accountSid, authToken) }.build()
    }
    data class SendResult(val verificationSid: String?, val status: String?, val expiresInSeconds: Long)
    data class CheckResult(val status: String?)

    fun sendSms(mobile: String, expiresInSeconds: Long): SendResult {
        requireReady()
        val form = LinkedMultiValueMap<String, String>().apply { add("To", toE164(mobile)); add("Channel", "sms") }
        return try {
            val response = restClient.post().uri("/Services/{serviceSid}/Verifications", verifyServiceSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map::class.java)
                ?: throw OtpDeliveryException("Unable to send OTP through Twilio: empty provider response")
            SendResult(response["sid"]?.toString(), response["status"]?.toString(), expiresInSeconds.coerceAtMost(600L))
        } catch (ex: RestClientException) {
            throw OtpDeliveryException("Unable to send OTP through Twilio. Check Twilio credentials and Verify Service configuration.", ex)
        }
    }

    fun checkSms(mobile: String, code: String): CheckResult {
        requireReady()
        val form = LinkedMultiValueMap<String, String>().apply { add("To", toE164(mobile)); add("Code", code) }
        return try {
            val response = restClient.post().uri("/Services/{serviceSid}/VerificationCheck", verifyServiceSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map::class.java)
                ?: throw OtpDeliveryException("Unable to verify OTP through Twilio: empty provider response")
            CheckResult(response["status"]?.toString())
        } catch (ex: RestClientException) {
            throw OtpDeliveryException("Unable to verify OTP through Twilio. Check Twilio credentials and Verify Service configuration.", ex)
        }
    }

    private fun requireReady() {
        if (!enabled) throw OtpDeliveryException("Twilio OTP delivery is disabled")
        if (accountSid.isBlank() || authToken.isBlank() || verifyServiceSid.isBlank()) {
            throw OtpDeliveryException("Twilio OTP credentials are not configured. Use password-reset provider 'mock' until Twilio is configured.")
        }
    }
    private fun toE164(mobile: String): String {
        val normalized = mobile.trim()
        require(Regex("[6-9][0-9]{9}").matches(normalized)) { "Mobile number must be a valid 10 digit Indian mobile number" }
        return "+91$normalized"
    }
}
