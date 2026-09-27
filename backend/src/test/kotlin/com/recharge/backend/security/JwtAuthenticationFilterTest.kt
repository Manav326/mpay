package com.recharge.backend.security

import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.UserRepository
import io.jsonwebtoken.Claims
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

class JwtAuthenticationFilterTest {

    private val jwtService = Mockito.mock(JwtService::class.java)
    private val users = Mockito.mock(UserRepository::class.java)
    private val filter = JwtAuthenticationFilter(jwtService, users)

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun downstreamExceptionIsNotCaughtOrReplayed() {
        val request = Mockito.mock(jakarta.servlet.http.HttpServletRequest::class.java)
        val response = Mockito.mock(jakarta.servlet.http.HttpServletResponse::class.java)
        val chain = Mockito.mock(jakarta.servlet.FilterChain::class.java)
        val claims = Mockito.mock(Claims::class.java)
        val user = UserEntity(id = 42L, mobile = "9876543210", role = "CLIENT")

        Mockito.doReturn("Bearer access-token").`when`(request).getHeader("Authorization")
        Mockito.doReturn(claims).`when`(jwtService).parseAndValidate("access-token")
        Mockito.doReturn(true).`when`(jwtService).isAccessToken(claims)
        Mockito.doReturn("42").`when`(claims).subject
        Mockito.doReturn(Optional.of(user)).`when`(users).findById(42L)
        Mockito.doThrow(ServletException("downstream failure"))
            .`when`(chain).doFilter(Mockito.any(ServletRequest::class.java), Mockito.any(ServletResponse::class.java))

        assertThrows(ServletException::class.java) {
            filter.doFilter(request, response, chain)
        }

        Mockito.verify(chain, Mockito.times(1)).doFilter(request, response)
    }

    @Test
    fun invalidTokenStillReachesSecurityChainExactlyOnce() {
        val request = Mockito.mock(jakarta.servlet.http.HttpServletRequest::class.java)
        val response = Mockito.mock(jakarta.servlet.http.HttpServletResponse::class.java)
        val chain = Mockito.mock(jakarta.servlet.FilterChain::class.java)

        Mockito.doReturn("Bearer expired-token").`when`(request).getHeader("Authorization")
        Mockito.doThrow(IllegalArgumentException("expired")).`when`(jwtService).parseAndValidate("expired-token")

        filter.doFilter(request, response, chain)

        Mockito.verify(chain, Mockito.times(1)).doFilter(request, response)
    }
}