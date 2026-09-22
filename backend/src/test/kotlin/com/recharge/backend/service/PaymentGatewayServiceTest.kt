package com.recharge.backend.service

import com.recharge.backend.api.CreatePaymentOrderRequest
import com.recharge.backend.api.CreatePaymentOrderResponse
import com.recharge.backend.api.VerifyPaymentRequest
import com.recharge.backend.api.VerifyPaymentResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

class PaymentGatewayServiceTest {
    @Test
    fun explicitProviderIsSelectedInsteadOfConfiguredFallback() {
        val mock = FakeGateway("mock")
        val razorpay = FakeGateway("razorpay")
        val service = PaymentGatewayService(listOf(mock, razorpay), Mockito.mock(RechargeService::class.java), "mock,razorpay")

        val response = service.createWalletOrder(
            1L,
            CreatePaymentOrderRequest(BigDecimal("100.00"), "REQ-12345678", "razorpay")
        )

        assertEquals("razorpay", response.provider)
        assertEquals(0, mock.createCalls)
        assertEquals(1, razorpay.createCalls)
    }

    @Test
    fun explicitUnconfiguredProviderDoesNotFallBack() {
        val mock = FakeGateway("mock")
        val razorpay = FakeGateway("razorpay", configured = true)
        val service = PaymentGatewayService(listOf(mock, razorpay), Mockito.mock(RechargeService::class.java), "mock,razorpay")

        org.junit.jupiter.api.assertThrows<ProviderNotConfiguredException> {
            service.createWalletOrder(
                1L,
                CreatePaymentOrderRequest(BigDecimal("100.00"), "REQ-UNCONFIG", "payu")
            )
        }
    }

    private class FakeGateway(
        override val providerName: String,
        private val configured: Boolean = true
    ) : PaymentGatewayProvider {
        var createCalls = 0

        override fun isConfigured(): Boolean = configured

        override fun createWalletOrder(userId: Long, request: CreatePaymentOrderRequest): CreatePaymentOrderResponse {
            createCalls++
            return CreatePaymentOrderResponse(providerName, "order-$providerName", request.amount, "INR", "key")
        }

        override fun verifyWalletPayment(userId: Long, request: VerifyPaymentRequest): VerifyPaymentResponse =
            VerifyPaymentResponse("CAPTURED", BigDecimal.ZERO)
    }
}
