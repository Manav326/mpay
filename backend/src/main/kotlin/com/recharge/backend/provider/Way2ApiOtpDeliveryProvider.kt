package com.recharge.backend.provider

import com.recharge.backend.service.OtpDeliveryProvider
import com.recharge.backend.service.OtpDeliveryResult
import com.recharge.backend.service.OtpPurpose
import com.recharge.backend.service.OtpDeliveryException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.ResourceAccessException
import java.time.Duration

@Component
class Way2ApiOtpDeliveryProvider(
    @Value("@@{app.way2api.base-url}") private val baseUrl: String,
    @Value("@@{app.way2api.api-key:}") private val apiKey: String,
    @Value("@@{app.way2api.otp-path:/api/v1/aws/send_otp-pin}") private val otpPath: String,
    @Value("@@{app.way2api.connect-timeout-ms:5000}") private val connectTimeoutMs: Long,
    @Value("@@{app.way2api.otp-read-timeout-ms:15000}") private val readTimeoutMs: Long
) : OtpDeliveryProvider {
    override val providerName: String = "way2api"

    private val http = RestClient.builder()
        .baseUrl(baseUrl.trimEnd('/'))
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofMillis(connectTimeoutMs.coerceAtLeast(100)))
                setReadTimeout(Duration.ofMillis(readTimeoutMs.coerceAtLeast(100)))
            }
        )
        .build()

    override fun send(mobile: String, otp: String, purpose: OtpPurpose): OtpDeliveryResult {
        if (apiKey.isBlank()) {
            throw OtpDeliveryException("Way2API OTP delivery is not configured. Add the Way2API API key.")
        }

        val request = Way2OtpRequest(
            number = "91$mobile",
            content = "OTP",
            value = otp,
            purpose = when (purpose) {
                OtpPurpose.PASSWORD_RESET -> "password_reset"
                OtpPurpose.REGISTRATION -> "registration"
            },
            share_with = "no_one"
        )

        return try {
            val response = http.post()
                .uri(otpPath)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(Way2OtpResponse::class.java)

            val body = response.body
                ?: throw OtpDeliveryException("Way2API returned an empty OTP response")

            if (!body.success) {
                throw OtpDeliveryException(
                    body.message?.takeIf { it.isNotBlank() }
                        ?: "Way2API could not send the OTP",
                    upstreamStatusCode = response.statusCode.value()
                )
            }

            val smsStatus = body.data?.result?.sms_status?.trim()?.lowercase()
            if (smsStatus != null && smsStatus != "sent") {
                throw OtpDeliveryException(
                    "Way2API did not confirm OTP delivery.",
                    upstreamStatusCode = response.statusCode.value()
                )
            }

            OtpDeliveryResult(
                provider = "way2api",
                providerOrderId = body.data?.order_id
            )
        } catch (ex: OtpDeliveryException) {
            throw ex
        } catch (ex: ResourceAccessException) {
            throw OtpDeliveryException("Way2API OTP delivery timed out or could not be reached.", cause = ex)
        } catch (ex: RestClientResponseException) {
            throw OtpDeliveryException(
                "Way2API OTP delivery failed. Please try again later.",
                upstreamStatusCode = ex.statusCode.value(),
                cause = ex
            )
        }
    }

    private data class Way2OtpRequest(
        val number: String,
        val content: String,
        val value: String,
        val purpose: String,
        val share_with: String
    )

    private data class Way2OtpResponse(
        val success: Boolean,
        val message: String?,
        val data: Way2OtpData?
    )

    private data class Way2OtpData(
        val order_id: String?,
        val result: Way2OtpResult?
    )

    private data class Way2OtpResult(
        val phone: String?,
        val content_type: String?,
        val purpose: String?,
        val share_with: String?,
        val sms_status: String?
    )
}
