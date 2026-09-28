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
import com.sun.net.httpserver.HttpServer
import java.math.BigDecimal
import java.net.InetSocketAddress
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
    }

    @Test
    fun verifyWalletPaymentSettlesWhenPayUReportsCapturedUnmappedStatus() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/verify") { exchange ->
            val body = """{"status":1,"msg":"1 out of 1 Transactions Fetched Successfully","transaction_details":{"MPAY-TEST-1":{"txnid":"MPAY-TEST-1","status":"captured","unmappedstatus":"captured","amt":"200.00","mihpayid":"70000012345"}}}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()

        try {
            val properties = com.recharge.backend.config.PayUProperties(
                pgKey = "test-key",
                pgSalt = "test-salt",
                pgVerifyUrl = "http://127.0.0.1:${server.address.port}/verify?form=2"
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
            val order = PaymentOrderEntity(
                clientRequestId = "REQ-PAYU-VERIFY-1",
                userId = 7L,
                razorpayOrderId = "MPAY-TEST-1",
                providerName = "payu",
                amount = BigDecimal("200.00"),
                status = "CREATED"
            )
            doReturn(Optional.of(order)).`when`(orders).findByRazorpayOrderIdAndUserId("MPAY-TEST-1", 7L)
            doReturn(com.recharge.backend.api.VerifyPaymentResponse(
                status = "CAPTURED",
                balance = BigDecimal("200.00"),
                availableBalance = BigDecimal("200.00")
            )).`when`(settlementService).settleCaptured(7L, order, "70000012345")
            doAnswer { invocation ->
                invocation.getArgument<PaymentOrderEntity>(0)
            }.`when`(orders).save(any(PaymentOrderEntity::class.java))

            val response = provider.verifyWalletPayment(
                userId = 7L,
                request = com.recharge.backend.api.VerifyPaymentRequest(
                    provider = "payu",
                    orderId = "MPAY-TEST-1"
                )
            )

            assertEquals("CAPTURED", response.status)
            assertEquals("70000012345", order.razorpayPaymentId)
            assertEquals("CAPTURED", order.status)
        } finally {
            server.stop(0)
        }
    }
}

