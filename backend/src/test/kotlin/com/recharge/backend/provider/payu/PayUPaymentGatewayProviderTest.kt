package com.recharge.backend.provider.payu

import com.recharge.backend.api.CreatePaymentOrderRequest
import com.recharge.backend.domain.PaymentOrderEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.PaymentOrderRepository
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.service.PaymentSettlementService
import com.recharge.backend.service.WalletService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.math.BigDecimal
import java.util.Optional

class PayUPaymentGatewayProviderTest {

    @Test
    fun createWalletOrderReturnsSavedOrderWithoutLockedRequery() {
        val properties = com.recharge.backend.config.PayUProperties(
            pgKey = "test-key",
            pgSalt = "test-salt",
            pgSuccessUrl = "https://example.test/success",
            pgFailureUrl = "https://example.test/failure"
        )
        val orders = mock(PaymentOrderRepository::class.java)
        val users = mock(UserRepository::class.java)
        val walletService = mock(WalletService::class.java)
        val settlementService = mock(PaymentSettlementService::class.java)
        val provider = PayUPaymentGatewayProvider(
            properties = properties,
            orders = orders,
            users = users,
            walletService = walletService,
            paymentSettlementService = settlementService
        )

        val user = UserEntity(id = 7L, mobile = "9876543210", name = "Test User", email = "test@example.com")
        doReturn(Optional.of(user)).`when`(users).findById(7L)
        doReturn(Optional.empty<PaymentOrderEntity>())
            .`when`(orders).findByClientRequestIdAndUserId("REQ-PAYU-1", 7L)
        doAnswer { invocation ->
            invocation.getArgument<PaymentOrderEntity>(0)
        }.`when`(orders).save(any(PaymentOrderEntity::class.java))

        val response = provider.createWalletOrder(
            userId = 7L,
            request = CreatePaymentOrderRequest(
                amount = BigDecimal("200.00"),
                provider = "payu",
                clientRequestId = "REQ-PAYU-1",
                purpose = "ADD_MONEY"
            )
        )

        assertEquals("payu", response.provider)
        assertEquals(BigDecimal("200.00"), response.amount)
        assertTrue(response.orderId.startsWith("MPAY"))
        assertEquals("test-key", response.keyId)
        verify(orders, never()).findByRazorpayOrderIdAndUserId(any(), any())
    }
}
