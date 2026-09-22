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
    fun mockGatewayCanCreateAndSettleAddMoneyOrder() {
        val orders = Mockito.mock(com.recharge.backend.repository.PaymentOrderRepository::class.java)
        val settlement = FakeSettlement()
        val provider = MockPaymentGatewayProvider(orders, settlement, true)
        val saved = com.recharge.backend.domain.PaymentOrderEntity(
            clientRequestId = "REQ-MOCK-1",
            userId = 1L,
            razorpayOrderId = "MOCK-PAY-1",
            providerName = "mock",
            amount = BigDecimal("100.00"),
            currency = "INR",
            status = "CREATED"
        )
        Mockito.doReturn(java.util.Optional.empty<com.recharge.backend.domain.PaymentOrderEntity>())
            .`when`(orders).findByClientRequestIdAndUserId("REQ-MOCK-1", 1L)
        Mockito.doAnswer { invocation -> invocation.getArgument<com.recharge.backend.domain.PaymentOrderEntity>(0) }
            .`when`(orders).save(Mockito.any())

        val created = provider.createWalletOrder(
            1L, CreatePaymentOrderRequest(amount = BigDecimal("100.00"), provider = "mock", clientRequestId = "REQ-MOCK-1")
        )
        assertEquals("mock", created.provider)
        org.junit.jupiter.api.Assertions.assertTrue(created.orderId.startsWith("MOCK-PAY-"))

        val settlementResponse = VerifyPaymentResponse("CAPTURED", BigDecimal("100.00"))
        Mockito.doReturn(java.util.Optional.of(saved)).`when`(orders).findByRazorpayOrderIdAndUserId(created.orderId, 1L)
        settlement.response = settlementResponse

        val verified = provider.verifyWalletPayment(1L, VerifyPaymentRequest("mock", null, created.orderId, null))
        assertEquals("CAPTURED", verified.status)
        assertEquals(1, settlement.settleCalls)
        assertEquals("CAPTURED", saved.status)
    }

    @Test
    fun explicitProviderIsSelectedInsteadOfConfiguredFallback() {
        val mock = FakeGateway("mock")
        val razorpay = FakeGateway("razorpay")
        val service = PaymentGatewayService(listOf(mock, razorpay), Mockito.mock(RechargeService::class.java), "mock,razorpay")

        val response = service.createWalletOrder(
            1L,
            CreatePaymentOrderRequest(amount = BigDecimal("100.00"), provider = "razorpay", clientRequestId = "REQ-12345678")
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
                CreatePaymentOrderRequest(amount = BigDecimal("100.00"), provider = "payu", clientRequestId = "REQ-UNCONFIG")
            )
        }
    }

    private class FakeSettlement : PaymentSettlementPort {
        var settleCalls = 0
        var response = VerifyPaymentResponse("CAPTURED", BigDecimal.ZERO)

        override fun responseForCaptured(userId: Long, order: com.recharge.backend.domain.PaymentOrderEntity): VerifyPaymentResponse = response

        override fun settleCaptured(
            userId: Long,
            order: com.recharge.backend.domain.PaymentOrderEntity,
            externalPaymentReference: String
        ): VerifyPaymentResponse {
            settleCalls++
            return response
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
