package com.recharge.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.payu")
data class PayUProperties(
    val environment: String = "test",
    val authBaseUrl: String = "https://uat-accounts.payu.in",
    val nbcBaseUrl: String = "https://bbps-sb.payu.in",
    val clientId: String = "",
    val clientSecret: String = "",
    val scope: String = "read_plans",
    val agentId: String = "",
    val plansPath: String = "/payu-nbc/v3/nbc/getRechargePlans",
    val planMockEnabled: Boolean = true,
    val operatorCodeMappings: Map<String, String> = emptyMap(),
    val circleCodeMappings: Map<String, String> = emptyMap(),
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 30_000
)
