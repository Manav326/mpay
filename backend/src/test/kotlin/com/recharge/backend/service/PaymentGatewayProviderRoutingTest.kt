package com.recharge.backend.service

import com.recharge.backend.api.CreatePaymentOrderRequest
import com.recharge.backend.api.CreatePaymentOrderResponse
import com.recharge.backend.api.VerifyPaymentRequest
import com.recharge.backend.api.VerifyPaymentResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal

class PaymentGatewayProviderRoutingTest {

    @Test
    fun selectsFirstConfiguredGatewayByConfiguredOrder() {
        val payu = FakeGateway("payu", configured = true)
        val razorpay = FakeGateway("razorpay", configured = true)
        val service = service(listOf(razorpay, payu), "payu,razorpay")

        val response = service.createWalletOrder(7L, orderRequest(provider = ""))

        assertEquals("payu", response.provider)
        assertEquals(1, payu.createCalls)
        assertEquals(0, razorpay.createCalls)
    }

    @Test
    fun skipsUnconfiguredPreferredGateway() {
        val payu = FakeGateway("payu", configured = false)
        val razorpay = FakeGateway("razorpay", configured = true)
        val service = service(listOf(payu, razorpay), "payu,razorpay")

        val response = service.createWalletOrder(7L, orderRequest(provider = ""))

        assertEquals("razorpay", response.provider)
        assertEquals(0, payu.createCalls)
        assertEquals(1, razorpay.createCalls)
    }

    @Test
    fun requestedProviderControlsVerification() {
        val payu = FakeGateway("payu", configured = true)
        val razorpay = FakeGateway("razorpay", configured = true)
        val service = service(listOf(payu, razorpay), "payu,razorpay")

        val response = service.verifyWalletPayment(
            7L,
            VerifyPaymentRequest(provider = "razorpay", paymentId = "pay_123")
        )

        assertEquals("razorpay", response.message)
        assertEquals(0, payu.verifyCalls)
        assertEquals(1, razorpay.verifyCalls)
    }

    @Test
    fun throwsWhenNoConfiguredGatewayIsAvailable() {
        val payu = FakeGateway("payu", configured = false)
        val razorpay = FakeGateway("razorpay", configured = false)
        val service = service(listOf(payu, razorpay), "payu,razorpay")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createWalletOrder(7L, orderRequest(provider = ""))
        }

        assertEquals("No configured payment gateway provider is available", ex.message)
    }

    private fun service(
        providers: List<PaymentGatewayProvider>,
        configuredOrder: String
    ) = PaymentGatewayService(
        providers = providers,
        rechargeService = mock(RechargeService::class.java),
        configuredProviders = configuredOrder
    )

    private fun orderRequest(provider: String) = CreatePaymentOrderRequest(
        amount = BigDecimal("299.00"),
        clientRequestId = "REQ-1",
        provider = provider
    )

    private class FakeGateway(
        override val providerName: String,
        private val configured: Boolean
    ) : PaymentGatewayProvider {

        var createCalls = 0
        var verifyCalls = 0

        override fun isConfigured(): Boolean = configured

        override fun createWalletOrder(
            userId: Long,
            request: CreatePaymentOrderRequest
        ): CreatePaymentOrderResponse {
            createCalls++
            return CreatePaymentOrderResponse(
                provider = providerName,
                orderId = "$providerName-order",
                amount = request.amount,
                currency = "INR",
                keyId = "$providerName-key"
            )
        }

        override fun verifyWalletPayment(
            userId: Long,
            request: VerifyPaymentRequest
        ): VerifyPaymentResponse {
            verifyCalls++
            return VerifyPaymentResponse(
                status = "SUCCESS",
                balance = BigDecimal("500.00"),
                message = providerName
            )
        }
    }
}
