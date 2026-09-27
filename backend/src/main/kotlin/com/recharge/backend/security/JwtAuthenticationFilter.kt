package com.recharge.backend.security

import com.recharge.backend.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
} catch (e: Exception) {
            SecurityContextHolder.clearContext()
}

        // Never wrap downstream request processing in the JWT exception handler.
        // Application/controller exceptions must propagate normally and the request
        // must never be replayed with an already-consumed request body.
        filterChain.doFilter(request, response)
    }
}
