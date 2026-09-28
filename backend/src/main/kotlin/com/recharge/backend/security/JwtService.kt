package com.recharge.backend.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${app.security.jwt-secret}") private val jwtSecret: String,
    @Value("\${app.security.access-token-minutes:30}") private val accessTokenMinutes: Long,
    @Value("\${app.security.refresh-token-days:30}") private val refreshTokenDays: Long
) {
    private val key: SecretKey by lazy {
        require(jwtSecret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "app.security.jwt-secret must be at least 32 bytes long"
        }
        Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))
    }

    fun createAccessToken(userId: Long, mobile: String, role: String): String =
        createToken(userId, mobile, role, "ACCESS", Instant.now().plusSeconds(accessTokenMinutes * 60))

    fun createRefreshToken(userId: Long, mobile: String, role: String): String =
        createToken(userId, mobile, role, "REFRESH", Instant.now().plusSeconds(refreshTokenDays * 24 * 60 * 60))

    fun parseAndValidate(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

    fun isAccessToken(claims: Claims): Boolean = claims["token_type"] == "ACCESS"
    fun isRefreshToken(claims: Claims): Boolean = claims["token_type"] == "REFRESH"
    fun isCallSignalingToken(claims: Claims): Boolean = claims["token_type"] == "CALL_SIGNAL"

    fun createCallSignalingToken(
        userId: Long,
        mobile: String,
        role: String,
        callId: String,
        ttlSeconds: Long
    ): String = createToken(
        userId = userId,
        mobile = mobile,
        role = role,
        tokenType = "CALL_SIGNAL",
        expiresAt = Instant.now().plusSeconds(ttlSeconds.coerceAtLeast(30)),
        extraClaims = mapOf("call_id" to callId)
    )

    private fun createToken(
        userId: Long,
        mobile: String,
        role: String,
        tokenType: String,
        expiresAt: Instant,
        extraClaims: Map<String, Any> = emptyMap()
    ): String {
        val now = Instant.now()
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .claim("mobile", mobile)
            .claim("role", role)
            .claim("token_type", tokenType)
            .also { builder -> extraClaims.forEach { (name, value) -> builder.claim(name, value) } }
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
    }
}
