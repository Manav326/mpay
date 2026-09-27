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
        val trace = request.requestURI == "/api/v1/wallet/withdraw" ||
            request.requestURI.startsWith("/api/v1/wallet/withdrawals")
        if (trace) {
            log.info(
                "WITHDRAW_AUTH_TRACE enter method={} uri={} dispatcher={} bearerPresent={} principal={} contextAuth={}",
                request.method,
                request.requestURI,
                request.dispatcherType,
                !header.isNullOrBlank() && header.startsWith("Bearer "),
                request.userPrincipal?.name,
                SecurityContextHolder.getContext().authentication?.name
            )
        }

        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            if (trace) {
                log.info(
                    "WITHDRAW_AUTH_TRACE exit_no_bearer method={} uri={} dispatcher={} principal={} contextAuth={} status={}",
                    request.method,
                    request.requestURI,
                    request.dispatcherType,
                    request.userPrincipal?.name,
                    SecurityContextHolder.getContext().authentication?.name,
                    response.status
                )
            }
            return
        }

        val token = header.removePrefix("Bearer ").trim()
        try {
            val claims = jwtService.parseAndValidate(token)
            if (!jwtService.isAccessToken(claims)) {
                filterChain.doFilter(request, response)
                if (trace) {
                    log.info(
                        "WITHDRAW_AUTH_TRACE exit_non_access method={} uri={} dispatcher={} principal={} contextAuth={} status={}",
                        request.method,
                        request.requestURI,
                        request.dispatcherType,
                        request.userPrincipal?.name,
                        SecurityContextHolder.getContext().authentication?.name,
                        response.status
                    )
                }
                return
            }

            val userId = claims.subject.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid JWT subject")
            val user = users.findById(userId).orElse(null)
            if (user == null || !user.active) {
                filterChain.doFilter(request, response)
                if (trace) {
                    log.info(
                        "WITHDRAW_AUTH_TRACE exit_missing_user method={} uri={} dispatcher={} userId={} principal={} contextAuth={} status={}",
                        request.method,
                        request.requestURI,
                        request.dispatcherType,
                        userId,
                        request.userPrincipal?.name,
                        SecurityContextHolder.getContext().authentication?.name,
                        response.status
                    )
                }
                return
            }

            val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role}"))
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId.toString(), null, authorities)
            if (trace) {
                log.info(
                    "WITHDRAW_AUTH_TRACE authenticated method={} uri={} dispatcher={} userId={} principal={} contextAuth={}",
                    request.method,
                    request.requestURI,
                    request.dispatcherType,
                    userId,
                    request.userPrincipal?.name,
                    SecurityContextHolder.getContext().authentication?.name
                )
            }
            filterChain.doFilter(request, response)
            if (trace) {
                log.info(
                    "WITHDRAW_AUTH_TRACE exit method={} uri={} dispatcher={} principal={} contextAuth={} status={}",
                    request.method,
                    request.requestURI,
                    request.dispatcherType,
                    request.userPrincipal?.name,
                    SecurityContextHolder.getContext().authentication?.name,
                    response.status
                )
            }
        } catch (e: Exception) {
            if (trace) {
                log.error(
                    "WITHDRAW_AUTH_TRACE authentication_failed method={} uri={} dispatcher={} type={} message={}",
                    request.method,
                    request.requestURI,
                    request.dispatcherType,
                    e.javaClass.name,
                    e.message,
                    e
                )
            }
            SecurityContextHolder.clearContext()
            filterChain.doFilter(request, response)
        }
    }
}
