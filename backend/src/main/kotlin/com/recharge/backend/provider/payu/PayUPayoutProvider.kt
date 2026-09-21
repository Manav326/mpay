package com.recharge.backend.provider.payu

import com.fasterxml.jackson.databind.JsonNode
import com.recharge.backend.config.PayUPayoutProperties
import com.recharge.backend.service.WithdrawalProvider
import com.recharge.backend.service.WithdrawalProviderRequest
import com.recharge.backend.service.WithdrawalProviderResult
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
class PayUPayoutProvider(
    private val properties: PayUPayoutProperties
) : WithdrawalProvider {

    override val providerName: String = "payu"

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAtMillis: Long = 0

    override fun isConfigured(): Boolean =
        properties.enabled &&
            properties.clientId.isNotBlank() &&
            properties.clientSecret.isNotBlank() &&
            properties.payoutMerchantId.isNotBlank()

    override fun initiate(request: WithdrawalProviderRequest): WithdrawalProviderResult {
        check(isConfigured()) { "PayU Payouts provider is not configured" }

        val payload = listOf(
            mapOf(
                "beneficiaryName" to request.customerName,
                "beneficiaryEmail" to request.customerEmail,
                "beneficiaryMobile" to request.customerMobile,
                "purpose" to "mPay wallet withdrawal",
                "amount" to request.amount.setScale(2),
                "batchId" to request.withdrawalId,
                "merchantRefId" to request.withdrawalId,
                "paymentType" to "UPI",
                "vpa" to request.upiId,
                "retry" to false
            )
        )

        val response = RestClient.builder()
            .baseUrl(properties.apiBaseUrl)
            .build()
            .post()
            .uri("/payout/v2/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token())
            .header("payoutMerchantId", properties.payoutMerchantId)
            .body(payload)
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("PayU Payouts response was empty")

        if (response.path("status").asInt(1) != 0) {
            val detail = response.path("data").path(0)
            val message = detail.path("error").asText().takeIf { it.isNotBlank() }
                ?: response.path("msg").asText().takeIf { it.isNotBlank() }
                ?: "PayU payout request failed"
            return WithdrawalProviderResult("FAILED", message = message, providerStatus = response.path("status").asText())
        }

        return WithdrawalProviderResult(
            status = "PROCESSING",
            providerStatus = "REQUEST_ACCEPTED",
            message = response.path("msg").asText().takeIf { it.isNotBlank() }
        )
    }

    private fun token(): String {
        val now = System.currentTimeMillis()
        accessToken?.takeIf { it.isNotBlank() && now < tokenExpiresAtMillis - 60_000 }?.let { return it }

        val form = listOf(
            "grant_type" to "client_credentials",
            "client_id" to properties.clientId,
            "client_secret" to properties.clientSecret,
            "scope" to properties.scope
        ).joinToString("&") { (key, value) ->
            enc(key) + "=" + enc(value)
        }

        val response = RestClient.builder()
            .baseUrl(properties.authBaseUrl)
            .build()
            .post()
            .uri("/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("PayU payout token response was empty")

        val token = response.path("access_token").asText().takeIf { it.isNotBlank() }
            ?: error("PayU payout access token was not returned")
        val expiresIn = response.path("expires_in").asLong(3600)

        accessToken = token
        tokenExpiresAtMillis = now + expiresIn * 1000
        return token
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
