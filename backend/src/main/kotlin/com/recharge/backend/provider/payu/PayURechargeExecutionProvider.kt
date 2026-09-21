package com.recharge.backend.provider.payu

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.recharge.backend.config.PayUProperties
import com.recharge.backend.provider.ProviderRechargeRequest
import com.recharge.backend.provider.ProviderRechargeResult
import com.recharge.backend.provider.RechargeExecutionProvider
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class PayURechargeExecutionProvider(
    private val properties: PayUProperties,
    private val auth: PayUAuthService,
    private val objectMapper: ObjectMapper
) : RechargeExecutionProvider {

    override val providerName: String = "payu"

    override fun isConfigured(): Boolean =
        auth.isConfigured() && properties.agentId.isNotBlank()

    private val http = RestClient.builder()
        .baseUrl(properties.nbcBaseUrl.trimEnd('/'))
        .build()

    override fun supportsOperator(operator: String): Boolean = operator.isNotBlank()

    override fun recharge(request: ProviderRechargeRequest): ProviderRechargeResult {
        check(auth.isConfigured()) { "PayU BBPS credentials are not configured" }
        check(properties.agentId.isNotBlank()) { "PayU agentId is not configured" }

        val refId = payURefId()
        val metadata = request.plan.providerMetadata
        val billerId = metadata["billerId"]?.takeIf { it.isNotBlank() }
            ?: metadata["operatorId"]?.takeIf { it.isNotBlank() }
            ?: throw PayUIntegrationException("PayU biller/operator id is missing for recharge")

        val paymentDetails = mutableMapOf<String, Any?>(
            "paymentMode" to properties.paymentMode
        )
        if (properties.paymentMode.equals("WALLET", true)) {
            paymentDetails["walletName"] = properties.walletName
        }

        val userDetails = mapOf(
            "mobileNo" to request.mobileNumber,
            "name" to "mPay Customer",
            "email" to "${request.mobileNumber}@mpay.local"
        )
        val deviceDetails = mapOf(
            "initChannel" to properties.initiatingChannel,
            "ip" to "127.0.0.1",
            "mac" to "00:00:00:00:00:00"
        )
        val planDetails = mapOf(
            "planName" to metadata["planName"].orEmpty(),
            "planType" to metadata["planType"].orEmpty(),
            "validity" to metadata["validity"].orEmpty()
        )

        val response = try {
            http.post()
                .uri { builder ->
                    builder.path(properties.billPaymentPath)
                        .queryParam("agentId", properties.agentId)
                        .queryParam("customerParams", objectMapper.writeValueAsString(listOf(request.mobileNumber)))
                        .queryParam("deviceDetails", objectMapper.writeValueAsString(deviceDetails))
                        .queryParam("paidAmount", request.plan.amount.setScale(2).toPlainString())
                        .queryParam("paymentName", "TOTAL")
                        .queryParam("refId", refId)
                        .queryParam("billerId", billerId)
                        .queryParam("paymentDetails", objectMapper.writeValueAsString(paymentDetails))
                        .queryParam("userDetails", objectMapper.writeValueAsString(userDetails))
                        .queryParam("isQuickPay", "false")
                        .queryParam("timeStamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .queryParam("planId", request.plan.id)
                        .queryParam("planDetails", objectMapper.writeValueAsString(planDetails))
                        .queryParam("additionalParams", objectMapper.writeValueAsString(emptyMap<String, Any>()))
                        .queryParam("paymentRefID", request.transactionId)
                        .queryParam("pgName", properties.pgName)
                        .build()
                }
                .header("Authorization", "Bearer ${auth.getAccessToken("create_transactions")}")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode::class.java)
        } catch (ex: Exception) {
            return ProviderRechargeResult(
                status = "PENDING",
                providerReference = refId,
                message = "PayU recharge submission could not be confirmed; transaction status will be checked."
            )
        }

        if (response == null) {
            return ProviderRechargeResult("PENDING", providerReference = refId, message = "PayU returned an empty recharge response.")
        }

        return mapResponse(response, refId)
    }

    override fun getStatus(transactionReference: String): ProviderRechargeResult? {
        if (transactionReference.isBlank()) return null
        check(auth.isConfigured()) { "PayU BBPS credentials are not configured" }

        val response = try {
            http.get()
                .uri { builder ->
                    builder.path(properties.statusPath)
                        .queryParam("refId", transactionReference)
                        .build()
                }
                .header("Authorization", "Bearer ${auth.getAccessToken("read_transactions")}")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode::class.java)
        } catch (ex: Exception) {
            return ProviderRechargeResult("PENDING", providerReference = transactionReference, message = "PayU status lookup could not be completed.")
        }

        if (response == null) return ProviderRechargeResult("PENDING", providerReference = transactionReference)

        val payload = response.path("payload")
        val status = payload.path("txnStatus").asText().uppercase()
        val message = payload.path("message").asText().takeIf { it.isNotBlank() }
        return when {
            status.contains("SUCCESS") -> ProviderRechargeResult(
                "SUCCESS",
                providerReference = transactionReference,
                message = message ?: "Recharge completed by PayU."
            )
            status.contains("FAILURE") || status.contains("FAILED") || status == "RECORD_NOT_FOUND" ->
                ProviderRechargeResult("FAILED", providerReference = transactionReference, message = message ?: "PayU reports recharge failure.")
            else -> ProviderRechargeResult("PENDING", providerReference = transactionReference, message = message ?: "PayU recharge is still pending.")
        }
    }

    private fun mapResponse(root: JsonNode, refId: String): ProviderRechargeResult {
        val payload = root.path("payload")
        val message = payload.path("message").asText().takeIf { it.isNotBlank() }
            ?: root.path("message").asText().takeIf { it.isNotBlank() }
        val providerReference = payload.path("additionalParams").path("txnReferenceId").asText().takeIf { it.isNotBlank() } ?: refId

        if (root.path("status").asText().equals("SUCCESS", true)) {
            return ProviderRechargeResult("SUCCESS", providerReference, message ?: "Recharge completed by PayU.")
        }

        return if (message.equals("payment_request_pending", true)) {
            ProviderRechargeResult("PENDING", refId, "Recharge is pending with PayU. We will verify the final status.")
        } else {
            ProviderRechargeResult("FAILED", providerReference, message ?: "PayU recharge request failed.")
        }
    }

    private fun payURefId(): String =
        ("MPAY" + UUID.randomUUID().toString().replace("-", "")).take(34)
}
