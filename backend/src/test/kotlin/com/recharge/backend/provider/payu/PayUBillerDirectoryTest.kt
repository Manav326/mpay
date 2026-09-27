package com.recharge.backend.provider.payu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PayUBillerDirectoryTest {

    @Test
    fun resolvesCommonMobileOperatorNamesFromPayUDirectory() {
        val billers = listOf(
            PayUBiller("AIRTEL-ID", "Bharti Airtel Prepaid", true),
            PayUBiller("VI-ID", "Vodafone Idea Limited", true),
            PayUBiller("JIO-ID", "Jio Prepaid", true),
            PayUBiller("BSNL-ID", "BSNL Mobile", true)
        )

        assertEquals("AIRTEL-ID", PayUBillerMatching.find("AIRTEL", billers).single().billerId)
        assertEquals("VI-ID", PayUBillerMatching.find("VI", billers).single().billerId)
        assertEquals("JIO-ID", PayUBillerMatching.find("JIO", billers).single().billerId)
        assertEquals("BSNL-ID", PayUBillerMatching.find("BSNL", billers).single().billerId)
    }

    @Test
    fun reportsAmbiguityInsteadOfGuessing() {
        val billers = listOf(
            PayUBiller("VI-1", "Vodafone Idea Limited", true),
            PayUBiller("VI-2", "Idea Prepaid", true)
        )

        assertEquals(
            2,
            PayUBillerMatching.find("VI", billers).size
        )
    }

    @Test
    fun unknownOperatorProducesNoCandidate() {
        val billers = listOf(
            PayUBiller("AIRTEL-ID", "Bharti Airtel Prepaid", true)
        )

        assertEquals(
            emptyList<PayUBiller>(),
            PayUBillerMatching.find("UNKNOWN", billers)
        )
    }
}
