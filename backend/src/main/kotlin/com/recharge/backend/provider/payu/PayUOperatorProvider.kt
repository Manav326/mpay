package com.recharge.backend.provider.payu

import com.fasterxml.jackson.databind.JsonNode
import com.recharge.backend.provider.OperatorCatalog
import com.recharge.backend.provider.OperatorDetectionProvider
import com.recharge.backend.provider.OperatorResult
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class PayUOperatorProvider(
    private val properties: com.recharge.backend.config.PayUProperties,
    private val auth: PayUAuthService
) : OperatorDetectionProvider {

    override val providerName: String = "payu"

    override fun isConfigured(): Boolean = auth.isConfigured() && properties.agentId.isNotBlank()

    private val http = RestClient.builder()
        .baseUrl(properties.nbcBaseUrl.trimEnd('/'))
        .build()

    override fun detectOperator(mobileNumber: String): OperatorResult {
        check(auth.isConfigured()) { "PayU BBPS credentials are not configured" }
        check(properties.agentId.isNotBlank()) { "PayU agentId is not configured" }

        val response = http.get()
            .uri { builder ->
                builder.path(properties.operatorCirclePath)
                    .queryParam("agentId", properties.agentId)
                    .queryParam("mobileNumber", mobileNumber)
                    .build()
            }
            .header("Authorization", "Bearer ${auth.getAccessToken("read_operator_circle")}")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw PayUIntegrationException("PayU returned an empty operator detection response")

        if (!response.path("status").asText().equals("SUCCESS", true)) {
            throw PayUIntegrationException(extractMessage(response) ?: "PayU operator detection failed")
        }

        val payload = response.path("payload")
        val info = firstOperatorCircle(payload)
            ?: throw PayUIntegrationException("PayU returned success without operator/circle details")

        val operatorName = firstText(info, "operatorName", "operator", "billerName").orEmpty()
        val operatorId = firstText(info, "operatorId", "operatorCode", "billerId").orEmpty()
        val circleName = firstText(info, "circleName", "circle", "circleDisplayName", "circleRefId").orEmpty()
        val circleId = firstText(info, "circleId", "circleRefId", "circleCode").orEmpty()
        val normalizedOperator = OperatorCatalog.normalize(operatorName.ifBlank { operatorId })
        val type = firstText(info, "type", "connectionType", "prepaidPostpaid")

        return OperatorResult(
            mobileNumber = mobileNumber,
            operator = normalizedOperator,
            providerOperator = operatorId.ifBlank { operatorName },
            circle = circleName.ifBlank { circleId },
            providerCircle = circleId.ifBlank { circleName },
            type = type,
            providerOrderId = firstText(info, "refId", "orderId"),
            status = "SUCCESS"
        )
    }

    private fun firstOperatorCircle(payload: JsonNode): JsonNode? {
        val candidates = sequenceOf(
            payload.path("operatorAndCircleInfo"),
            payload.path("circlesInfo"),
            payload.path("circleWisePlanLists")
        ).flatMap { node ->
            when {
                node.isArray -> node.asSequence()
                node.isObject -> sequenceOf(node)
                else -> emptySequence()
            }
        }
        for (candidate in candidates) {
            val nested = candidate.path("operatorAndCircleInfo")
            if (nested.isArray && nested.size() > 0) return nested[0]
            if (nested.isObject) return nested
            if (candidate.has("operatorId") || candidate.has("operatorName") || candidate.has("operatorCode")) return candidate
        }
        if (payload.has("operatorId") || payload.has("operatorName") || payload.has("operatorCode")) return payload
        return null
    }

    private fun firstText(node: JsonNode, vararg names: String): String? =
        names.asSequence()
            .map { node.path(it) }
            .filter { it.isValueNode && !it.isNull }
            .map { it.asText().trim() }
            .firstOrNull { it.isNotBlank() }

    private fun extractMessage(node: JsonNode): String? {
        val direct = firstText(node, "message")
        if (!direct.isNullOrBlank()) return direct
        val errors = node.path("payload").path("errors")
        if (errors.isArray && errors.size() > 0) {
            return firstText(errors[0], "reason", "message", "errorMessage")
        }
        return null
    }
}
