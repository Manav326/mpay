package com.recharge.backend.provider.payu

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.recharge.backend.config.PayUProperties
import com.recharge.backend.provider.PlanCatalogProvider
import com.recharge.backend.provider.RechargePlan
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@Service
@ConditionalOnProperty(prefix = "app.recharge", name = ["plan-provider"], havingValue = "payu")
class PayUPlanProvider(
    private val properties: PayUProperties,
    private val authService: PayUAuthService,
    private val objectMapper: ObjectMapper
) : PlanCatalogProvider {

    private val client = RestClient.builder()
        .baseUrl(properties.nbcBaseUrl.trimEnd('/'))
        .build()

    override fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan> {
        require(mobileNumber.matches(Regex("[6-9][0-9]{9}"))) {
            "Mobile number must be a valid 10 digit Indian mobile number"
        }
        require(operator.isNotBlank()) { "Operator is required" }
        require(circle.isNotBlank()) { "Circle is required" }

        // Until the newly created PayU credentials are verified, we intentionally use
        // a development-only deterministic mock. This lets us finish and test our
        // internal contract without pretending PayU authentication succeeded.
        if (properties.planMockEnabled) {
            return mockPlans(operator, circle)
        }

        if (!authService.isConfigured()) {
            throw PayUIntegrationException("PayU credentials are not configured and plan mock is disabled")
        }

        val accessToken = authService.getAccessToken()
        val operatorId = properties.operatorCodeMappings[operator.uppercase()]
            ?: throw PayUIntegrationException("No PayU operatorId mapping configured for '$operator'")
        val circleId = properties.circleCodeMappings[circle]
            ?: properties.circleCodeMappings.entries.firstOrNull { it.key.equals(circle, ignoreCase = true) }?.value
            ?: throw PayUIntegrationException("No PayU circleId mapping configured for '$circle'")
        val agentId = properties.agentId.trim()
        require(agentId.isNotBlank()) { "PayU agent-id is required for plan lookup" }

        val response = client.get()
            .uri { builder ->
                builder.path(properties.plansPath)
                    .queryParam("agentId", agentId)
                    .queryParam("circleId", circleId)
                    .queryParam("operatorId", operatorId)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .retrieve()
            .body(String::class.java)
            ?: throw PayUIntegrationException("PayU plans API returned an empty response")

        return parsePlans(response, operator, circle)
    }

    private fun parsePlans(raw: String, operator: String, circle: String): List<RechargePlan> {
        val root = try {
            objectMapper.readTree(raw)
        } catch (ex: Exception) {
            throw PayUIntegrationException("PayU plans API returned invalid JSON", ex)
        }

        val status = root.path("status").asText()
        if (!status.equals("SUCCESS", ignoreCase = true)) {
            val message = root.path("message").asText().takeIf { it.isNotBlank() }
                ?: root.path("payload").path("errors").firstOrNull()?.path("reason")?.asText()
                ?: "PayU plans API failed"
            throw PayUIntegrationException("PayU plans lookup failed for $operator / $circle: $message")
        }

        val payload = root.path("payload")
        val plans = mutableListOf<RechargePlan>()

        if (payload.isArray) {
            // Current PayU documented response: payload[] -> circleWisePlanLists[] -> plansInfo[].
            payload.forEach { operatorNode ->
                operatorNode.path("circleWisePlanLists").forEach { circleNode ->
                    val circleName = circleNode.path("circleName").asText()
                    if (circleName.isBlank() || circleName.equals(circle, ignoreCase = true)) {
                        circleNode.path("plansInfo").forEach { plan ->
                            plans += toRechargePlan(plan)
                        }
                    }
                }
            }
        } else if (payload.isObject) {
            // Custom-plan API shape: payload.plansInfo[].
            payload.path("plansInfo").forEach { plan ->
                plans += toRechargePlan(plan)
            }
        }

        return plans
            .filter { it.amount.signum() > 0 }
            .distinctBy { it.id }
    }

    private fun toRechargePlan(plan: JsonNode): RechargePlan {
        val planName = plan.path("planName").asText().ifBlank { "Recharge plan" }
        val price = plan.path("price").asText()
            .ifBlank { plan.path("amount").asText() }
            .toBigDecimalOrNull()
            ?: throw PayUIntegrationException("PayU returned a plan without a valid price")
        val planType = plan.path("planType").asText().takeIf { it.isNotBlank() }
        val validity = plan.path("validity").asText().takeIf { it.isNotBlank() }
        val validityDescription = plan.path("validityDescription").asText().takeIf { it.isNotBlank() }
        val talkTime = plan.path("talkTime").asText().takeIf { it.isNotBlank() }
        val packageDescription = plan.path("packageDescription").asText().takeIf { it.isNotBlank() }

        val idSource = listOf(planType, planName, price.toPlainString(), validity).filterNotNull().joinToString("|")
        val id = "PAYU-${idSource.hashCode().toUInt().toString(16)}"
        val description = listOf(validityDescription, packageDescription, talkTime)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { planName }

        return RechargePlan(
            id = id,
            amount = price,
            validity = validity,
            description = description
        )
    }

    private fun mockPlans(operator: String, circle: String): List<RechargePlan> {
        val prefix = "MOCK-${operator.uppercase()}-${circle.hashCode().toUInt().toString(16)}"
        return listOf(
            RechargePlan(
                id = "$prefix-199",
                amount = BigDecimal("199.00"),
                validity = "28 days",
                description = "Mock UAT plan • Unlimited voice + 1.5 GB/day + 100 SMS/day"
            ),
            RechargePlan(
                id = "$prefix-299",
                amount = BigDecimal("299.00"),
                validity = "28 days",
                description = "Mock UAT plan • Unlimited voice + 2 GB/day + 100 SMS/day"
            ),
            RechargePlan(
                id = "$prefix-399",
                amount = BigDecimal("399.00"),
                validity = "56 days",
                description = "Mock UAT plan • Unlimited voice + 2.5 GB/day + 100 SMS/day"
            )
        )
    }
}
