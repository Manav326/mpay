package com.recharge.backend.service

import com.recharge.backend.api.CreatePaymentOrderRequest
import com.recharge.backend.api.CreatePaymentOrderResponse
import com.recharge.backend.api.VerifyPaymentRequest
import com.recharge.backend.api.VerifyPaymentResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class PaymentGatewayService(
    private val providers: List<PaymentGatewayProvider>,
    private val rechargeService: RechargeService,
    @Value("\${app.payment.gateway-providers:payu,razorpay}") private val configuredProviders: String
) {
    fun createWalletOrder(userId: Long, request: CreatePaymentOrderRequest): CreatePaymentOrderResponse =
        resolveProvider().createWalletOrder(userId, request)

    fun createRechargeOrder(userId: Long, request: com.recharge.backend.api.RechargeRequest): CreatePaymentOrderResponse =
        resolveProvider().createWalletOrder(userId, rechargeService.createRechargePaymentOrder(userId, request))

    fun verifyWalletPayment(userId: Long, request: VerifyPaymentRequest): VerifyPaymentResponse {
        val requested = request.provider.trim()
        if (requested.isNotBlank()) {
            providers.firstOrNull { it.isConfigured() && it.providerName.equals(requested, true) }?.let {
                return it.verifyWalletPayment(userId, request)
            }
        }
        return resolveProvider().verifyWalletPayment(userId, request)
    }

    private fun resolveProvider(): PaymentGatewayProvider {
        configuredProviders.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { name ->
            providers.firstOrNull { it.isConfigured() && it.providerName.equals(name, true) }?.let { return it }
        }
        throw IllegalArgumentException("No configured payment gateway provider is available")
    }
}
