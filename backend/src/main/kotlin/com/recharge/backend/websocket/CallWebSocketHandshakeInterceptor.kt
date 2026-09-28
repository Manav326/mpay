package com.recharge.backend.websocket

import com.recharge.backend.repository.UserRepository
import com.recharge.backend.security.JwtService
import com.recharge.backend.service.VoiceCallService
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder

@Component
class CallWebSocketHandshakeInterceptor(
    private val jwtService: JwtService,
    private val users: UserRepository,
    private val calls: VoiceCallService
) : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val queryToken = UriComponentsBuilder.fromUri(request.uri)
            .build()
            .queryParams
            .getFirst("token")

        val headerToken = request.headers.getFirst("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        val token = queryToken?.takeIf { it.isNotBlank() } ?: headerToken
        if (token.isNullOrBlank()) {
            response.statusCode = HttpStatus.UNAUTHORIZED
            return false
        }

        return try {
            val claims = jwtService.parseAndValidate(token)
            if (!jwtService.isCallSignalingToken(claims)) {
                response.statusCode = HttpStatus.UNAUTHORIZED
                false
            } else {
                val userId = claims.subject.toLongOrNull()
                val callId = claims["call_id"]?.toString()
                val user = userId?.let { users.findById(it).orElse(null) }
                if (userId == null || callId.isNullOrBlank() || user == null || !user.active || !calls.socketAuthorized(userId, callId)) {
                    response.statusCode = HttpStatus.FORBIDDEN
                    false
                } else {
                    attributes["userId"] = userId
                    attributes["callId"] = callId
                    true
                }
            }
        } catch (_: Exception) {
            response.statusCode = HttpStatus.UNAUTHORIZED
            false
        }
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) = Unit
}
