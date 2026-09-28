package com.recharge.backend.websocket

import com.recharge.backend.config.CallProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class CallWebSocketConfig(
    private val handler: CallWebSocketHandler,
    private val interceptor: CallWebSocketHandshakeInterceptor,
    private val properties: CallProperties,
    @Value("\${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private val allowedOriginsRaw: String
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        val origins = allowedOriginsRaw.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        registry.addHandler(handler, properties.websocketPath)
            .addInterceptors(interceptor)
            .setAllowedOrigins(*origins.toTypedArray())
    }
}
