package com.recharge.backend.provider

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class Way2ApiProvider(
    @Value("\${app.way2api.base-url}") private val baseUrl: String,
    @Value("\${app.way2api.api-key}") private val apiKey: String,
    @Value("\${app.way2api.operator-check-path}") private val operatorPath: String
) : RechargeProvider {

    private val http = RestClient.builder().baseUrl(baseUrl).build()

    override fun detectOperator(mobileNumber: String): OperatorResult {
        val request = Way2OperatorCheckRequest(mobile_number = mobileNumber)
        val response: ResponseEntity<Way2OperatorCheckResponse> = http.post()
            .uri(operatorPath)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toEntity(Way2OperatorCheckResponse::class.java)

        val root = response.body ?: error("Way2API returned an empty response")
        if (!root.success || root.status != "SUCCESS") {
            val code = root.message_code?.takeIf { it.isNotBlank() }
            val message = root.message?.takeIf { it.isNotBlank() }
            error(buildString {
                append("Operator detection failed")
                if (code != null) append(" [$code]")
                if (message != null) append(": $message")
            })
        }

        val result = root.data?.result ?: error("Way2API returned no operator result")
        val operator = OperatorCatalog.normalize(result.operator)

        return OperatorResult(
            mobileNumber = result.mobile_number,
            operator = operator,
            providerOperator = result.operator,
            circle = result.circle,
            type = result.type,
            providerOrderId = root.order_id ?: root.data.order_id
        )
    }

    override fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan> {
        // Plan retrieval is now delegated to PayUPlanProvider. This method remains
        // for backward compatibility with the existing RechargeProvider abstraction.
        return emptyList()
    }

    override fun recharge(userId: Long, mobileNumber: String, planId: String): ProviderRechargeResult {
        // Deliberately left behind the provider abstraction. The final recharge execution
        // API contract has not been supplied yet.
        return ProviderRechargeResult("PENDING", message = "Recharge execution provider is not configured yet")
    }

    private data class Way2OperatorCheckRequest(
        val mobile_number: String
    )

    private data class Way2OperatorCheckResponse(
        val status: String,
        val status_code: Int,
        val charged: Boolean,
        val success: Boolean,
        val message: String?,
        val message_code: String?,
        val order_id: String?,
        val data: Way2OperatorData?
    )

    private data class Way2OperatorData(
        val order_id: String?,
        val result: Way2OperatorResult?
    )

    private data class Way2OperatorResult(
        val mobile_number: String,
        val operator: String,
        val circle: String,
        val type: String?
    )
}

object OperatorCatalog {
    fun normalize(providerOperator: String): String = when {
        providerOperator.equals("AIRTEL", ignoreCase = true) -> "AIRTEL"
        providerOperator.equals("JIO", ignoreCase = true) -> "JIO"
        providerOperator.equals("VI", ignoreCase = true) -> "VI"
        providerOperator.equals("VODAFONE", ignoreCase = true) -> "VI"
        providerOperator.equals("VODAFONE IDEA", ignoreCase = true) -> "VI"
        providerOperator.equals("IDEA", ignoreCase = true) -> "VI"
        providerOperator.equals("BSNL", ignoreCase = true) -> "BSNL"
        else -> providerOperator.trim().uppercase()
    }
}
