package com.recharge.backend.security

import com.recharge.backend.repository.EmployeeRepository
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
    private val users: UserRepository,
    private val employees: EmployeeRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)

        if (!header.isNullOrBlank() && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ").trim()
            try {
                val claims = jwtService.parseAndValidate(token)
                if (jwtService.isAccessToken(claims)) {
                    val accountId = claims.subject.toLongOrNull()
                        ?: throw IllegalArgumentException("Invalid JWT subject")
                    val accountType = jwtService.accountType(claims)

                    val active = when (accountType) {
                        "EMPLOYEE" -> employees.findById(accountId).map { it.active }.orElse(false)
                        else -> users.findById(accountId).map { it.active }.orElse(false)
                    }

                    if (active) {
                        val role = claims["role"]?.toString()
                            ?: when (accountType) {
                                "EMPLOYEE" -> employees.findById(accountId).orElseThrow().role
                                else -> users.findById(accountId).orElseThrow().role
                            }
                        val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
                        SecurityContextHolder.getContext().authentication =
                            UsernamePasswordAuthenticationToken(accountId.toString(), null, authorities)
                        request.setAttribute("mpayAccountType", accountType)
                    } else {
                        SecurityContextHolder.clearContext()
                    }
                } else {
                    SecurityContextHolder.clearContext()
                }
            } catch (_: Exception) {
                SecurityContextHolder.clearContext()
            }
        }

        filterChain.doFilter(request, response)
    }
}
