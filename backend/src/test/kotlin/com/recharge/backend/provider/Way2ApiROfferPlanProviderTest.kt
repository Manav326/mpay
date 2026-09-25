package com.recharge.backend.provider

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Way2ApiROfferPlanProviderTest {

    private val provider = Way2ApiROfferPlanProvider(
        baseUrl = "http://localhost",
        apiKey = "test",
        rOfferPath = "/api/v1/mobile/r-offer",
        rechargePlansPath = "/api/v1/mobile/recharge-plans",
        connectTimeoutMs = 1000,
        rOfferReadTimeoutMs = 1000,
        readTimeoutMs = 1000,
        objectMapper = ObjectMapper()
    )

    @Test
    fun supportsAllWay2ApiPlanOperators() {
        assertTrue(provider.supportsOperator("AIRTEL"))
        assertTrue(provider.supportsOperator("VI"))
        assertTrue(provider.supportsOperator("JIO"))
        assertTrue(provider.supportsOperator("BSNL"))
    }

    @Test
    fun normalizesBiharAndJharkhandToWay2ApiCircleEnum() {
        val method = provider.javaClass.getDeclaredMethod("normalizeCircle", String::class.java)
        method.isAccessible = true

        assertEquals("bihar", method.invoke(provider, "Bihar and Jharkhand"))
        assertEquals("bihar", method.invoke(provider, "Bihar & Jharkhand"))
        assertEquals("bihar", method.invoke(provider, "bihar"))
    }
}
