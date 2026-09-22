package com.recharge.backend.provider.razorpay

import com.fasterxml.jackson.databind.JsonNode
import com.recharge.backend.config.RazorpayPayoutProperties
import com.recharge.backend.service.WithdrawalProvider
import com.recharge.backend.service.WithdrawalProviderRequest
import com.recharge.backend.service.WithdrawalProviderResult
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@Service
class RazorpayPayoutProvider(
    private val properties: RazorpayPayoutProperties
) : WithdrawalProvider {

    override val providerName: String = "razorpay"

    override fun isConfigured(): Boolean =
        properties.enabled &&
            properties.keyId.isNotBlank() &&
            properties.keySecret.isNotBlank() &&
            properties.accountNumber.isNotBlank() &&
            properties.contactId.isNotBlank()

    override fun initiate(request: WithdrawalProviderRequest): WithdrawalProviderResult {
        check(isConfigured()) { "RazorpayX payout provider is not configured" }

        val client = RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeaders { headers ->
                headers.setBasicAuth(properties.keyId, properties.keySecret)
            }
            .build()

        val fundAccount = client.post()
            .uri("/fund_accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "account_type" to "vpa",
                    "contact_id" to properties.contactId,
                    "vpa" to mapOf("address" to request.upiId)
                )
            )
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("RazorpayX fund-account response was empty")

        val fundAccountId = fundAccount.path("id").asText().takeIf { it.isNotBlank() }
            ?: error("RazorpayX did not return a fund-account id")

        val payoutAmountPaise = request.amount
            .multiply(BigDecimal("100"))
            .longValueExact()

        val payout = client.post()
            .uri("/payouts")
            .header("X-Payout-Idempotency", request.withdrawalId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "account_number" to properties.accountNumber,
                    "fund_account_id" to fundAccountId,
                    "amount" to payoutAmountPaise,
                    "currency" to "INR",
                    "mode" to "UPI",
                    "purpose" to properties.purpose,
                    "queue_if_low_balance" to false,
                    "reference_id" to request.withdrawalId,
                    "narration" to "mPay UPI withdrawal"
                )
            )
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("RazorpayX payout response was empty")

        val status = payout.path("status").asText().uppercase()
        val reference = payout.path("id").asText().takeIf { it.isNotBlank() }
        val message = payout.path("status_details").path("description").asText().takeIf { it.isNotBlank() }

        return when (status) {
            "PROCESSED" -> WithdrawalProviderResult("SUCCESS", reference, message, status)
            "FAILED", "REVERSED" -> WithdrawalProviderResult("FAILED", reference, message ?: "RazorpayX payout failed", status)
            else -> WithdrawalProviderResult("PROCESSING", reference, message, status.ifBlank { "QUEUED" })
        }
    }
}
