package com.recharge.backend.provider.payu

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.recharge.backend.config.PayUProperties
import com.recharge.backend.provider.PlanCatalogProvider
import com.recharge.backend.provider.RechargePlan
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.security.MessageDigest

@Service
class PayUPlanProvider(
    private val properties: PayUProperties,
    private val authService: PayUAuthService,
    private val objectMapper: ObjectMapper
) : PlanCatalogProvider {

    override val providerName: String = "payu"

    private val client = RestClient.builder()
        .baseUrl(properties.nbcBaseUrl.trimEnd('/'))
        .build()

    override fun supportsOperator(operator: String): Boolean = operator.isNotBlank()

    override fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan> =
        getPlans(mobileNumber, operator, circle, null, null)

    override fun getPlans(
        mobileNumber: String,
        operator: String,
        circle: String,
        providerOperator: String?,
        providerCircle: String?
    ): List<RechargePlan> {
        require(mobileNumber.matches(Regex("[6-9][0-9]{9}"))) { "Mobile number must be a valid 10 digit Indian mobile number" }
        require(operator.isNotBlank()) { "Operator is required" }
        require(circle.isNotBlank()) { "Circle is required" }
        check(authService.isConfigured()) { "PayU BBPS credentials are not configured" }
        check(properties.agentId.isNotBlank()) { "PayU agentId is not configured" }

        val rawOperator = providerOperator?.trim().orEmpty()
        val rawCircle = providerCircle?.trim().orEmpty()
        val operatorId = properties.operatorCodeMappings[rawOperator]
            ?: properties.operatorCodeMappings.entries.firstOrNull { it.key.equals(rawOperator, true) }?.value
            ?: rawOperator.takeIf { it.isNotBlank() }
            ?: throw PayUIntegrationException("PayU operator ID is missing from operator detection")
        val circleId = properties.circleCodeMappings[rawCircle]
            ?: properties.circleCodeMappings.entries.firstOrNull { it.key.equals(rawCircle, true) }?.value
            ?: rawCircle.takeIf { it.isNotBlank() }
            ?: throw PayUIntegrationException("PayU circle ID is missing from operator detection")

        val accessToken = authService.getAccessToken("read_plans")
        val response = client.get()
            .uri { builder ->
                builder.path(properties.customPlansPath)
                    .queryParam("agentId", properties.agentId)
                    .queryParam("circleId", circleId)
                    .queryParam("mobileNo", mobileNumber)
                    .queryParam("operatorId", operatorId)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .retrieve()
            .body(String::class.java)
            ?: throw PayUIntegrationException("PayU plans API returned an empty response")

        return parsePlans(response, operator, circle, operatorId, circleId)
    }

    private fun parsePlans(
        raw: String,
        operator: String,
        circle: String,
        operatorId: String,
        circleId: String
    ): List<RechargePlan> {
        val root = runCatching { objectMapper.readTree(raw) }
            .getOrElse { throw PayUIntegrationException("PayU plans API returned invalid JSON", it) }

        if (!root.path("status").asText().equals("SUCCESS", true)) {
            val message = root.path("message").asText().takeIf { it.isNotBlank() }
                ?: root.path("payload").path("errors").firstOrNull()?.path("reason")?.asText()
                ?: "PayU plans API failed"
            throw PayUIntegrationException("PayU plans lookup failed for $operator / $circle: $message")
        }

        val payload = root.path("payload")
        val nodes = mutableListOf<JsonNode>()

        if (payload.isArray) {
            payload.forEach { operatorNode ->
                operatorNode.path("circleWisePlanLists").forEach { circleNode ->
                    if (
                        circleNode.path("circleId").asText().equals(circleId, true) ||
                        circleNode.path("circleName").asText().equals(circle, true)
                    ) {
                        circleNode.path("plansInfo").forEach(nodes::add)
                    }
                }
            }
        } else if (payload.isObject) {
            payload.path("plansInfo").forEach(nodes::add)
            payload.path("circleWisePlanLists").forEach { circleNode ->
                circleNode.path("plansInfo").forEach(nodes::add)
            }
        }

        return nodes.map { toRechargePlan(it, operatorId, circleId) }
            .filter { it.amount.signum() > 0 }
            .distinctBy { it.id }
    }

    private fun toRechargePlan(plan: JsonNode, operatorId: String, circleId: String): RechargePlan {
        val planName = plan.path("planName").asText().ifBlank { "Recharge plan" }
        val price = firstDecimal(plan, "price", "amount")
            ?: throw PayUIntegrationException("PayU returned a plan without a valid price")
        val planType = plan.path("planType").asText().takeIf { it.isNotBlank() }
        val validity = plan.path("validity").asText().takeIf { it.isNotBlank() }
        val validityDescription = plan.path("validityDescription").asText().takeIf { it.isNotBlank() }
        val talkTime = plan.path("talkTime").asText().takeIf { it.isNotBlank() }
        val packageDescription = plan.path("packageDescription").asText().takeIf { it.isNotBlank() }
        val idSource = listOf(planType, planName, price.toPlainString(), validity).filterNotNull().joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(idSource.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(20)

        return RechargePlan(
            id = "PAYU-$digest",
            amount = price,
            validity = validity,
            description = listOf(validityDescription, packageDescription, talkTime)
                .filterNotNull().filter { it.isNotBlank() }.joinToString(" • ").ifBlank { planName },
            providerMetadata = mapOf(
                "operatorId" to operatorId,
                "billerId" to operatorId,
                "circleId" to circleId,
                "planName" to planName,
                "planType" to (planType ?: ""),
                "validity" to (validity ?: "")
            )
        )
    }

    private fun firstDecimal(node: JsonNode, vararg names: String): BigDecimal? =
        names.asSequence().map { node.path(it).asText() }
            .mapNotNull { it.toBigDecimalOrNull() }
            .firstOrNull()
}
