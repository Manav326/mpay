package com.recharge.backend.provider.payu

import com.fasterxml.jackson.databind.ObjectMapper
import com.recharge.backend.config.PayUProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Service
class PayUBillerDirectory(
    private val properties: PayUProperties,
    private val auth: PayUAuthService,
    private val objectMapper: ObjectMapper
) {
    private val http = RestClient.builder()
        .baseUrl(properties.nbcBaseUrl.trimEnd('/'))
        .build()

    private val cached = AtomicReference<CachedBillers?>(null)
    private val cacheTtl: Duration = Duration.ofHours(1)

    fun resolveBillerId(operator: String, candidateBillerId: String?): String? {
        val billers = activeBillers()

        candidateBillerId?.trim()?.takeIf { it.isNotBlank() }?.let { candidate ->
            billers.firstOrNull { it.billerId.equals(candidate, ignoreCase = true) }?.let { return it.billerId }
        }

        val matches = PayUBillerMatching.find(operator, billers)
        return when (matches.size) {
            0 -> null
            1 -> matches.first().billerId
            else -> throw PayUIntegrationException(
                "PayU returned multiple active billers matching " +
                    operator.trim().uppercase() + ": " +
                    matches.joinToString { it.billerName }
            )
        }
    }

    private fun activeBillers(): List<PayUBiller> {
        val now = Instant.now()
        val existing = cached.get()
        if (existing != null && existing.expiresAt.isAfter(now)) return existing.billers

        synchronized(this) {
            val refreshed = cached.get()
            if (refreshed != null && refreshed.expiresAt.isAfter(Instant.now())) return refreshed.billers

            check(auth.isConfigured()) { "PayU BBPS credentials are not configured" }
            check(properties.agentId.isNotBlank()) { "PayU agentId is not configured" }

            val raw = try {
                http.get()
                    .uri { builder ->
                        builder.path(properties.billerByCategoryPath)
                            .queryParam("billerCategoryName", properties.billerCategoryName)
                            .queryParam("agentId", properties.agentId)
                            .build()
                    }
                    .header("Authorization", "Bearer " + auth.getAccessToken("read_billers"))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String::class.java)
            } catch (ex: Exception) {
                throw PayUIntegrationException("PayU biller directory lookup failed", ex)
            } ?: throw PayUIntegrationException("PayU biller directory returned an empty response")

            val billers = parseBillers(raw)
            cached.set(CachedBillers(billers, Instant.now().plus(cacheTtl)))
            return billers
        }
    }

    private fun parseBillers(raw: String): List<PayUBiller> {
        val root = try {
            objectMapper.readTree(raw)
        } catch (ex: Exception) {
            throw PayUIntegrationException("PayU biller directory returned invalid JSON", ex)
        }

        if (!root.path("status").asText().equals("SUCCESS", true)) {
            val message = root.path("payload").path("errors").firstOrNull()?.path("reason")?.asText()
                ?.takeIf { it.isNotBlank() }
                ?: root.path("message").asText().takeIf { it.isNotBlank() }
                ?: "PayU biller directory lookup failed"
            throw PayUIntegrationException(message)
        }

        val nodes = root.path("payload").path("billers")
        if (!nodes.isArray) {
            throw PayUIntegrationException("PayU biller directory response did not contain billers")
        }

        return nodes.mapNotNull { node ->
            val id = node.path("billerId").asText().trim()
            val name = node.path("billerName").asText().trim()
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            val status = node.path("status").asText().trim()
            PayUBiller(
                billerId = id,
                billerName = name,
                active = status.isBlank() || status.equals("ACTIVE", true)
            )
        }.filter { it.active }
    }

    private data class CachedBillers(
        val billers: List<PayUBiller>,
        val expiresAt: Instant
    )
}

internal data class PayUBiller(
    val billerId: String,
    val billerName: String,
    val active: Boolean = true
)

internal object PayUBillerMatching {
    fun find(operator: String, billers: List<PayUBiller>): List<PayUBiller> {
        val aliases = aliasesFor(operator)
        if (aliases.isEmpty()) return emptyList()

        return billers.filter { biller ->
            val normalizedName = normalize(biller.billerName)
            aliases.any { alias -> normalizedName.contains(alias) }
        }
    }

    private fun aliasesFor(operator: String): Set<String> {
        return when (normalize(operator)) {
            "AIRTEL" -> setOf("AIRTEL")
            "VI", "VODAFONEIDEA", "VODAFONE", "IDEA" -> setOf("VODAFONE", "IDEA")
            "JIO" -> setOf("JIO")
            "BSNL" -> setOf("BSNL")
            else -> normalize(operator).takeIf { it.isNotBlank() }?.let { setOf(it) }.orEmpty()
        }
    }

    private fun normalize(value: String): String =
        value.uppercase().replace(Regex("[^A-Z0-9]"), "")
}
