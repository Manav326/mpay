package com.recharge.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class SupportAiConfiguration {
    @Bean("supportAiExecutor")
    fun supportAiExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 100
            setThreadNamePrefix("support-ai-")
            initialize()
        }

    @Bean
    fun supportAiRestClient(properties: SupportAiProperties): RestClient {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(properties.timeoutMs.coerceAtLeast(1000)))
            setReadTimeout(Duration.ofMillis(properties.timeoutMs.coerceAtLeast(1000)))
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl.trimEnd('/'))
            .requestFactory(factory)
            .build()
    }
}
