package com.recharge.backend.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.recharge.backend.config.PayUPayoutProperties
import com.recharge.backend.config.RazorpayPayoutProperties
import com.recharge.backend.service.WithdrawalService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
@RequestMapping("/api/v1/webhooks")
class PayoutWebhookController(
    private val withdrawals: WithdrawalService,
    private val razorpayProperties: RazorpayPayoutProperties,
    private val payuProperties: PayUPayoutProperties,
    private val objectMapper: ObjectMapper
) {
    @PostMapping("/razorpay/payout")
    fun razorpayPayout(
        @RequestHeader("X-Razorpay-Signature", required = false) signature: String?,
        @RequestBody body: String
    ): ResponseEntity<Void> {
        verifyHmac(body, signature, razorpayProperties.webhookSecret, "RazorpayX")
        val payload = objectMapper.readTree(body)
        val event = payload.path("event").asText()
        val entity = payload.path("payload").path("payout").path("entity")
        val merchantReference = entity.path("reference_id").asText().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Razorpay payout reference_id is missing")
        val providerReference = entity.path("id").asText().takeIf { it.isNotBlank() }
        val providerStatus = entity.path("status").asText().takeIf { it.isNotBlank() }
        val status = when {
            event.endsWith(".processed", true) || providerStatus.equals("processed", true) -> "SUCCESS"
            event.endsWith(".failed", true) || providerStatus.equals("failed", true) -> "FAILED"
            event.endsWith(".reversed", true) || providerStatus.equals("reversed", true) -> "REVERSED"
            else -> "PROCESSING"
        }
        withdrawals.handleWebhook(
            providerName = "razorpay",
            merchantReference = merchantReference,
            status = status,
            providerReference = providerReference,
            providerStatus = providerStatus,
            message = entity.path("status_details").path("description").asText().takeIf { it.isNotBlank() }
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/payu/payout")
    fun payuPayout(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody body: String
    ): ResponseEntity<Void> {
        if (payuProperties.webhookAuthorization.isBlank() ||
            !MessageDigest.isEqual(
                payuProperties.webhookAuthorization.toByteArray(StandardCharsets.UTF_8),
                (authorization ?: "").toByteArray(StandardCharsets.UTF_8)
            )
        ) {
            throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Invalid PayU payout webhook authorization"
            )
        }

        val payload = objectMapper.readTree(body)
        val merchantReference = payload.path("merchantReferenceId").asText().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("PayU merchantReferenceId is missing")
        val event = payload.path("event").asText().uppercase()
        val providerReference = payload.path("payuRefId").asText().takeIf { it.isNotBlank() }
        val providerStatus = payload.path("event").asText().takeIf { it.isNotBlank() }
        val status = when (event) {
            "TRANSFER_SUCCESS" -> "SUCCESS"
            "TRANSFER_FAILED" -> "FAILED"
            "TRANSFER_REVERSED" -> "REVERSED"
            else -> "PROCESSING"
        }
        withdrawals.handleWebhook(
            providerName = "payu",
            merchantReference = merchantReference,
            status = status,
            providerReference = providerReference,
            providerStatus = providerStatus,
            message = payload.path("msg").asText().takeIf { it.isNotBlank() }
        )
        return ResponseEntity.ok().build()
    }

    private fun verifyHmac(body: String, providedSignature: String?, secret: String, provider: String) {
        require(secret.isNotBlank()) { "$provider payout webhook secret is not configured" }
        val actual = hmacSha256(body, secret)
        require(
            providedSignature != null &&
                MessageDigest.isEqual(
                    actual.toByteArray(StandardCharsets.UTF_8),
                    providedSignature.toByteArray(StandardCharsets.UTF_8)
                )
        ) { "Invalid $provider payout webhook signature" }
    }

    private fun hmacSha256(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
