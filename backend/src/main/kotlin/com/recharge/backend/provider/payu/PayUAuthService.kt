package com.recharge.backend.provider.payu

import com.fasterxml.jackson.annotation.JsonProperty
import com.recharge.backend.config.PayUProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.Instant

@Service
class PayUAuthService(
    private val properties: PayUProperties
) {
    private val restClient = RestClient.builder()
        .baseUrl(properties.authBaseUrl.trimEnd('/'))
        .build()

    private val cachedTokens = mutableMapOf<String, CachedToken>()

    @Synchronized
    fun getAccessToken(scope: String = properties.scope): String {
        val normalizedScope = scope.trim().ifBlank { properties.scope }
        val cached = cachedTokens[normalizedScope]
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return cached.value
        }

        requireConfigured()

        val response = try {
            restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(
                    "client_id=${encode(properties.clientId)}" +
                        "&client_secret=${encode(properties.clientSecret)}" +
                        "&grant_type=client_credentials" +
                        "&scope=${encode(normalizedScope)}"
                )
                .retrieve()
                .body(PayUTokenResponse::class.java)
        } catch (ex: RestClientResponseException) {
            throw PayUIntegrationException(
                "PayU token request failed with HTTP ${ex.statusCode.value()}",
                ex
            )
        } catch (ex: RestClientException) {
            throw PayUIntegrationException("PayU token request failed", ex)
        } ?: throw PayUIntegrationException("PayU returned an empty token response")

        if (response.accessToken.isBlank()) {
            throw PayUIntegrationException("PayU token response did not contain access_token")
        }

        val expiresIn = response.expiresIn.coerceAtLeast(60)
        val token = CachedToken(
            value = response.accessToken,
            expiresAt = Instant.now().plusSeconds(expiresIn.toLong())
        )
        cachedTokens[normalizedScope] = token
        return token.value
    }

    @Synchronized
    fun clearToken(scope: String? = null) {
        if (scope.isNullOrBlank()) cachedTokens.clear() else cachedTokens.remove(scope.trim())
    }

    fun isConfigured(): Boolean = properties.clientId.isNotBlank() && properties.clientSecret.isNotBlank()

    fun environment(): String = properties.environment
    fun scope(): String = properties.scope
    fun authBaseUrl(): String = properties.authBaseUrl
    fun nbcBaseUrl(): String = properties.nbcBaseUrl

    private fun requireConfigured() {
        check(isConfigured()) {
            "PayU is not configured. Add app.payu.client-id and app.payu.client-secret to config/application-secrets.yml"
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private data class CachedToken(
        val value: String,
        val expiresAt: Instant
    )
}

data class PayUTokenResponse(
    @JsonProperty("access_token") val accessToken: String = "",
    @JsonProperty("token_type") val tokenType: String = "Bearer",
    @JsonProperty("expires_in") val expiresIn: Int = 0,
    val scope: String? = null,
    @JsonProperty("created_at") val createdAt: Long? = null
)

class PayUIntegrationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
