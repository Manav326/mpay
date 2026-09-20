package com.recharge.backend.api.provider

import com.recharge.backend.provider.payu.PayUAuthService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/provider/payu")
class PayUProviderController(
    private val authService: PayUAuthService
) {
    @GetMapping("/health")
    fun health(authentication: Authentication): ResponseEntity<PayUHealthResponse> {
        // Authentication is intentionally required. This endpoint is a development/UAT
        // diagnostic endpoint and must never expose credentials or raw bearer tokens.
        if (!authService.isConfigured()) {
            return ResponseEntity.ok(
                PayUHealthResponse(
                    configured = false,
                    authenticated = false,
                    environment = authService.environment(),
                    scope = authService.scope(),
                    message = "PayU credentials are not configured"
                )
            )
        }

        return try {
            authService.getAccessToken()
            ResponseEntity.ok(
                PayUHealthResponse(
                    configured = true,
                    authenticated = true,
                    environment = authService.environment(),
                    scope = authService.scope(),
                    message = "PayU authentication succeeded"
                )
            )
        } catch (ex: Exception) {
            ResponseEntity.status(502).body(
                PayUHealthResponse(
                    configured = true,
                    authenticated = false,
                    environment = authService.environment(),
                    scope = authService.scope(),
                    message = ex.message ?: "PayU authentication failed"
                )
            )
        }
    }
}

data class PayUHealthResponse(
    val configured: Boolean,
    val authenticated: Boolean,
    val environment: String,
    val scope: String,
    val message: String
)
