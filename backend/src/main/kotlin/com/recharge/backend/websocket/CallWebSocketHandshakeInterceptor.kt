package com.recharge.backend.websocket

import com.recharge.backend.repository.EmployeeRepository
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
    private val employees: EmployeeRepository,
    private val calls: VoiceCallService
) : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val queryToken = UriComponentsBuilder.fromUri(request.uri).build().queryParams.getFirst("token")
        val headerToken = request.headers.getFirst("Authorization")?.removePrefix("Bearer ")?.trim()
        val token = queryToken?.takeIf { it.isNotBlank() } ?: headerToken
        if (token.isNullOrBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }

        return try {
            val claims = jwtService.parseAndValidate(token)
            if (!jwtService.isCallSignalingToken(claims)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED)
                false
            } else {
                val accountId = claims.subject.toLongOrNull()
                val accountType = jwtService.accountType(claims)
                val callId = claims["call_id"]?.toString()
                val active = when (accountType) {
                    "EMPLOYEE" -> accountId?.let { employees.findById(it).map { employee -> employee.active }.orElse(false) } ?: false
                    else -> accountId?.let { users.findById(it).map { user -> user.active && user.deletedAt == null }.orElse(false) } ?: false
                }
                if (
                    accountId == null ||
                    callId.isNullOrBlank() ||
                    !active ||
                    !calls.socketAuthorized(accountType, accountId, callId)
                ) {
                    response.setStatusCode(HttpStatus.FORBIDDEN)
                    false
                } else {
                    attributes["accountId"] = accountId
                    attributes["accountType"] = accountType
                    attributes["callId"] = callId
                    true
                }
            }
        } catch (_: Exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
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
