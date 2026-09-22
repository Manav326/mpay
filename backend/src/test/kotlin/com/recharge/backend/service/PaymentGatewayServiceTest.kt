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
        val settlement = Mockito.mock(PaymentSettlementService::class.java)
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
            1L, CreatePaymentOrderRequest(BigDecimal("100.00"), "REQ-MOCK-1", "mock")
        )
        assertEquals("mock", created.provider)
        assertEquals("MOCK-PAY-1", created.orderId)

        val settlementResponse = VerifyPaymentResponse("CAPTURED", BigDecimal("100.00"))
        Mockito.doReturn(java.util.Optional.of(saved)).`when`(orders).findByRazorpayOrderIdAndUserId("MOCK-PAY-1", 1L)
        Mockito.doReturn(settlementResponse).`when`(settlement).settleCaptured(1L, saved, "MOCK-PAY-1")

        val verified = provider.verifyWalletPayment(1L, VerifyPaymentRequest("mock", null, "MOCK-PAY-1", null))
        assertEquals("CAPTURED", verified.status)
        assertEquals("CAPTURED", saved.status)
    }

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
