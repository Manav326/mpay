package com.recharge.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.support-ai")
data class SupportAiProperties(
    val providerEnabled: Boolean = false,
    val apiKey: String = "",
    val model: String = "gpt-5.6-luna",
    val vectorStoreId: String = "",
    val baseUrl: String = "https://api.openai.com",
    val timeoutMs: Long = 15_000,
    val maxHistoryMessages: Int = 12,
    val maxKnowledgeCharacters: Int = 8_000
) {
    val providerConfigured: Boolean
        get() = providerEnabled && apiKey.isNotBlank()
}
