package com.recharge.backend.service

import com.recharge.backend.api.OperatorCheckRequest
import com.recharge.backend.provider.OperatorDetectionProvider
import com.recharge.backend.provider.OperatorResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal

class OperatorProviderRoutingTest {

    @Test
    fun usesConfiguredProviderOrderAndDoesNotCallLaterProviderAfterSuccess() {
        val way2 = FakeOperatorProvider("way2api") {
            result(operator = "AIRTEL", providerOperator = "AIRTEL")
        }
        val payu = FakeOperatorProvider("payu") {
            result(operator = "AIRTEL", providerOperator = "AIRTEL")
        }
        val service = service(listOf(payu, way2), "way2api,payu")

        val response = service.detect(OperatorCheckRequest("9876543210"))

        assertEquals("way2api", way2.providerName)
        assertEquals("AIRTEL", response.operator)
        assertEquals(1, way2.calls)
        assertEquals(0, payu.calls)
    }

    @Test
    fun fallsBackToNextProviderWhenFirstProviderFails() {
        val way2 = FakeOperatorProvider("way2api") {
            throw IllegalStateException("Way2API unavailable")
        }
        val payu = FakeOperatorProvider("payu") {
            result(operator = "VI", providerOperator = "VODAFONE IDEA")
        }
        val service = service(listOf(way2, payu), "way2api,payu")

        val response = service.detect(OperatorCheckRequest("9876543210"))

        assertEquals("VI", response.operator)
        assertEquals(1, way2.calls)
        assertEquals(1, payu.calls)
    }

    @Test
    fun doesNotFallbackWhenFirstProviderReturnsPending() {
        val way2 = FakeOperatorProvider("way2api") {
            result(
                operator = "",
                providerOperator = "",
                status = "PENDING",
                pending = true,
                message = "Provider is still processing"
            )
        }
        val payu = FakeOperatorProvider("payu") {
            result(operator = "AIRTEL", providerOperator = "AIRTEL")
        }
        val service = service(listOf(way2, payu), "way2api,payu")

        val response = service.detect(OperatorCheckRequest("9876543210"))

        assertEquals("PENDING", response.rechargeStatus)
        assertEquals(true, response.pending)
        assertEquals(1, way2.calls)
        assertEquals(0, payu.calls)
    }

    @Test
    fun skipsUnconfiguredProvider() {
        val way2 = FakeOperatorProvider("way2api", configured = false) {
            result(operator = "AIRTEL", providerOperator = "AIRTEL")
        }
        val payu = FakeOperatorProvider("payu") {
            result(operator = "JIO", providerOperator = "JIO")
        }
        val service = service(listOf(way2, payu), "way2api,payu")

        val response = service.detect(OperatorCheckRequest("9876543210"))

        assertEquals("JIO", response.operator)
        assertEquals(0, way2.calls)
        assertEquals(1, payu.calls)
    }

    @Test
    fun throwsWhenAllConfiguredProvidersFail() {
        val way2 = FakeOperatorProvider("way2api") {
            throw IllegalStateException("Way2API unavailable")
        }
        val payu = FakeOperatorProvider("payu") {
            throw IllegalArgumentException("PayU unavailable")
        }
        val service = service(listOf(way2, payu), "way2api,payu")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.detect(OperatorCheckRequest("9876543210"))
        }

        assertEquals("PayU unavailable", ex.message)
        assertEquals(1, way2.calls)
        assertEquals(1, payu.calls)
    }

    private fun service(
        providers: List<OperatorDetectionProvider>,
        order: String
    ): RechargeService =
        RechargeService(
            operatorProviders = providers,
            offerService = mock(RechargeOfferService::class.java),
            executionProviders = emptyList(),
            workflow = mock(RechargeTransactionWorkflowService::class.java),
            rechargeRepository = mock(com.recharge.backend.repository.RechargeTransactionRepository::class.java),
            walletService = mock(WalletService::class.java),
            companyPercent = BigDecimal.ZERO,
            commissionRateService = mock(CommissionRateService::class.java),
            operatorProviderOrder = order,
            executionProviderOrder = "payu"
        )

    private fun result(
        operator: String,
        providerOperator: String,
        status: String = "SUCCESS",
        pending: Boolean = false,
        message: String? = null
    ) = OperatorResult(
        mobileNumber = "9876543210",
        operator = operator,
        providerOperator = providerOperator,
        circle = "Bihar and Jharkhand",
        providerCircle = "10",
        type = "PREPAID",
        providerOrderId = "OP-123",
        status = status,
        pending = pending,
        message = message
    )

    private class FakeOperatorProvider(
        override val providerName: String,
        private val configured: Boolean = true,
        private val handler: () -> OperatorResult
    ) : OperatorDetectionProvider {

        var calls: Int = 0
            private set

        override fun isConfigured(): Boolean = configured

        override fun detectOperator(mobileNumber: String): OperatorResult {
            calls++
            return handler()
        }
    }
}
