package com.recharge.backend.security

import com.recharge.backend.repository.UserRepository
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val users: UserRepository
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val trace = request.requestURI == "/api/v1/wallet/withdraw" ||
            request.requestURI.startsWith("/api/v1/wallet/withdrawals")

        val token = header.removePrefix("Bearer ").trim()
        try {
            val claims = jwtService.parseAndValidate(token)
            if (!jwtService.isAccessToken(claims)) {
                filterChain.doFilter(request, response)
                return
            }

            val userId = claims.subject.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid JWT subject")
            val user = users.findById(userId).orElse(null)
            if (user == null || !user.active) {
                filterChain.doFilter(request, response)
                return
            }

            val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role}"))
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId.toString(), null, authorities)

            if (trace) {
                log.info(
                    "WITHDRAW_AUTH_TRACE authenticated method={} uri={} dispatcher={} userId={}",
                    request.method,
                    request.requestURI,
                    request.dispatcherType,
                    userId
                )
            }
        } catch (e: Exception) {
            SecurityContextHolder.clearContext()
            if (trace) {
                log.error(
                    "WITHDRAW_AUTH_TRACE authentication_failed method={} uri={} dispatcher={} type={} message={}",
                    request.method,
                    request.requestURI,
                    request.dispatcherType,
                    e.javaClass.name,
                    e.message
                )
            }
        }

        // Never wrap downstream request processing in the JWT exception handler.
        // Application/controller exceptions must propagate normally and the request
        // must never be replayed with an already-consumed request body.
        filterChain.doFilter(request, response)
    }
}
