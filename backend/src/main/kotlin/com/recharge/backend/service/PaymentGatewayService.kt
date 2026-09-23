package com.recharge.backend.service

import com.recharge.backend.api.CreatePaymentOrderRequest
import com.recharge.backend.api.CreatePaymentOrderResponse
import com.recharge.backend.api.VerifyPaymentRequest
import com.recharge.backend.api.VerifyPaymentResponse
import com.recharge.backend.domain.PaymentOrderEntity
import com.recharge.backend.repository.PaymentOrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class PaymentGatewayService(
    private val providers: List<PaymentGatewayProvider>,
    private val rechargeService: RechargeService,
    @Value("\${app.payment.gateway-providers:razorpay,payu}") private val configuredProviders: String
) {
    fun createWalletOrder(userId: Long, request: CreatePaymentOrderRequest): CreatePaymentOrderResponse {
        val requested = request.provider.trim()
        if (requested.isNotBlank()) {
            return findConfiguredProvider(requested).createWalletOrder(userId, request)
        }
        return resolveProvider().createWalletOrder(userId, request)
    }

    fun createRechargeOrder(
        userId: Long,
        request: com.recharge.backend.api.RechargeRequest
    ): CreatePaymentOrderResponse =
        resolveProvider().createWalletOrder(
            userId,
            rechargeService.createRechargePaymentOrder(userId, request)
        )

    fun verifyWalletPayment(userId: Long, request: VerifyPaymentRequest): VerifyPaymentResponse {
        val requested = request.provider.trim()
        if (requested.isNotBlank()) {
            return findConfiguredProvider(requested).verifyWalletPayment(userId, request)
        }
        return resolveProvider().verifyWalletPayment(userId, request)
    }

    fun configuredProviderNames(): List<String> =
        configuredProviders.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun findConfiguredProvider(name: String): PaymentGatewayProvider =
        providers.firstOrNull {
            it.isConfigured() && it.providerName.equals(name.trim(), true)
        } ?: throw ProviderNotConfiguredException("Payment gateway provider is not configured: " + name.trim())

    private fun resolveProvider(): PaymentGatewayProvider {
        configuredProviders
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { name ->
                providers.firstOrNull {
                    it.isConfigured() && it.providerName.equals(name, true)
                }?.let { return it }
            }
        throw ProviderNotConfiguredException("No configured payment gateway provider is available")
    }
}

class ProviderNotConfiguredException(message: String) : RuntimeException(message)

@Service
class MockPaymentGatewayProvider(
    private val orders: PaymentOrderRepository,
    private val paymentSettlementService: PaymentSettlementPort,
    @Value("\${app.payment.mock.enabled:true}") private val enabled: Boolean
) : PaymentGatewayProvider {

    override val providerName: String = "mock"

    override fun isConfigured(): Boolean = enabled

    override fun createWalletOrder(
        userId: Long,
        request: CreatePaymentOrderRequest
    ): CreatePaymentOrderResponse {
        check(isConfigured()) { "Mock payment provider is disabled" }

        val amount = request.amount.setScale(2)
        require(amount in BigDecimal("1.00")..BigDecimal("50000.00")) {
            "Payment amount must be between ₹1 and ₹50,000"
        }

        val clientRequestId = request.clientRequestId.trim()
        require(clientRequestId.length in 8..80) { "Invalid client request id" }

        val existing = orders.findByClientRequestIdAndUserId(clientRequestId, userId)
        if (existing.isPresent) {
            val order = existing.get()
            require(order.providerName.equals(providerName, true)) {
                "A different payment provider already owns this request id"
            }
            return responseFor(order)
        }

        val orderId = "MOCK-PAY-" + UUID.randomUUID().toString().replace("-", "").take(24).uppercase()
        val order = orders.save(
            PaymentOrderEntity(
                clientRequestId = clientRequestId,
                userId = userId,
                razorpayOrderId = orderId,
                providerName = providerName,
                amount = amount,
                currency = "INR",
                status = "CREATED",
                purpose = request.purpose,
                rechargeMobileNumber = request.rechargeMobileNumber,
                rechargeOperator = request.rechargeOperator,
                rechargeCircle = request.rechargeCircle,
                rechargePlanId = request.rechargePlanId,
                rechargeRecipientName = request.rechargeRecipientName
            )
        )

        return responseFor(order)
    }

    @Transactional
    override fun verifyWalletPayment(
        userId: Long,
        request: VerifyPaymentRequest
    ): VerifyPaymentResponse {
        check(isConfigured()) { "Mock payment provider is disabled" }

        val orderId = request.orderId?.trim().orEmpty()
        require(orderId.isNotBlank()) { "Mock payment order id is required" }

        val order = orders.findByRazorpayOrderIdAndUserId(orderId, userId)
            .orElseThrow { IllegalArgumentException("Mock payment order not found") }
        require(order.providerName.equals(providerName, true)) {
            "Payment order belongs to another gateway"
        }

        if (order.status == "CAPTURED") {
            return paymentSettlementService.responseForCaptured(userId, order)
        }

        val response = paymentSettlementService.settleCaptured(
            userId = userId,
            order = order,
            externalPaymentReference = order.razorpayOrderId
        )

        order.status = "CAPTURED"
        order.razorpayPaymentId = order.razorpayOrderId
        order.verifiedAt = Instant.now()
        orders.save(order)

        return response
    }

    private fun responseFor(order: PaymentOrderEntity): CreatePaymentOrderResponse =
        CreatePaymentOrderResponse(
            provider = providerName,
            orderId = order.razorpayOrderId,
            amount = order.amount,
            currency = order.currency,
            keyId = "mock",
            checkoutParams = mapOf("mode" to "mock")
        )
}
