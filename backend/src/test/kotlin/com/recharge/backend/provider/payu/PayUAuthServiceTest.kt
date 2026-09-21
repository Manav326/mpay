package com.recharge.backend.provider.payu

import com.recharge.backend.config.PayUProperties
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class PayUAuthServiceTest {

    private lateinit var server: HttpServer
    private lateinit var responses: ArrayDeque<TokenResponse>
    private lateinit var auth: PayUAuthService
    private lateinit var requests: MutableList<Map<String, String>>

    @BeforeEach
    fun setUp() {
        responses = ArrayDeque()
        requests = mutableListOf()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/oauth/token") { exchange -> handle(exchange) }
        server.start()

        auth = PayUAuthService(
            PayUProperties(
                authBaseUrl = "http://127.0.0.1:${server.address.port}",
                clientId = "test-client",
                clientSecret = "test-secret",
                scope = "read_plans"
            )
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun requestsClientCredentialsTokenWithExpectedFormFields() {
        responses.add(TokenResponse("token-1", 7200))

        val token = auth.getAccessToken()

        assertEquals("token-1", token)
        assertEquals(
            mapOf(
                "client_id" to "test-client",
                "client_secret" to "test-secret",
                "grant_type" to "client_credentials",
                "scope" to "read_plans"
            ),
            requests.single()
        )
    }

    @Test
    fun reusesCachedTokenForSameScope() {
        responses.add(TokenResponse("token-1", 7200))

        assertEquals("token-1", auth.getAccessToken("read_plans"))
        assertEquals("token-1", auth.getAccessToken("read_plans"))

        assertEquals(1, requests.size)
    }

    @Test
    fun keepsTokensSeparateForDifferentScopes() {
        responses.add(TokenResponse("plans-token", 7200))
        responses.add(TokenResponse("transaction-token", 7200))

        assertEquals("plans-token", auth.getAccessToken("read_plans"))
        assertEquals("transaction-token", auth.getAccessToken("create_transactions"))
        assertEquals(2, requests.size)
        assertEquals("read_plans", requests[0]["scope"])
        assertEquals("create_transactions", requests[1]["scope"])
    }

    @Test
    fun blankRequestedScopeFallsBackToConfiguredDefaultScope() {
        responses.add(TokenResponse("token-1", 7200))

        assertEquals("token-1", auth.getAccessToken("   "))

        assertEquals("read_plans", requests.single()["scope"])
    }

    @Test
    fun refreshesTokenWhenRemainingLifetimeIsTooShort() {
        responses.add(TokenResponse("token-1", 60))
        responses.add(TokenResponse("token-2", 7200))

        assertEquals("token-1", auth.getAccessToken())
        assertEquals("token-2", auth.getAccessToken())

        assertEquals(2, requests.size)
    }

    @Test
    fun clearTokenForOneScopeDoesNotClearOtherScopes() {
        responses.add(TokenResponse("plans-token", 7200))
        responses.add(TokenResponse("transactions-token", 7200))
        responses.add(TokenResponse("plans-token-2", 7200))

        assertEquals("plans-token", auth.getAccessToken("read_plans"))
        assertEquals("transactions-token", auth.getAccessToken("create_transactions"))

        auth.clearToken("read_plans")

        assertEquals("plans-token-2", auth.getAccessToken("read_plans"))
        assertEquals("transactions-token", auth.getAccessToken("create_transactions"))
        assertEquals(3, requests.size)
    }

    @Test
    fun rejectsEmptyAccessToken() {
        responses.add(TokenResponse("", 7200))

        val ex = assertThrows(PayUIntegrationException::class.java) {
            auth.getAccessToken()
        }

        assertEquals("PayU token response did not contain access_token", ex.message)
    }

    @Test
    fun rejectsWhenCredentialsAreMissing() {
        val unconfigured = PayUAuthService(
            PayUProperties(
                authBaseUrl = "http://127.0.0.1:${server.address.port}",
                scope = "read_plans"
            )
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            unconfigured.getAccessToken()
        }

        assertEquals(
            "PayU is not configured. Add app.payu.client-id and app.payu.client-secret to config/application-secrets.yml",
            ex.message
        )
    }

    private fun handle(exchange: HttpExchange) {
        requests += parseForm(exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8))

        val response = synchronized(responses) {
            if (responses.isEmpty()) TokenResponse("unexpected", 7200) else responses.removeFirst()
        }

        val body = """{"access_token":"${response.accessToken}","token_type":"Bearer","expires_in":${response.expiresIn},"scope":"${requests.last()["scope"]}","created_at":1}"""
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.toByteArray(StandardCharsets.UTF_8).size.toLong())
        exchange.responseBody.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split("&")
            .filter { it.isNotBlank() }
            .associate { item ->
                val parts = item.split("=", limit = 2)
                val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8)
                val value = URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8)
                key to value
            }

    private data class TokenResponse(
        val accessToken: String,
        val expiresIn: Int
    )
}
