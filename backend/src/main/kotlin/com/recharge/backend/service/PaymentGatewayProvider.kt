package com.recharge.backend.service

import com.recharge.backend.api.CreatePaymentOrderRequest
import com.recharge.backend.api.CreatePaymentOrderResponse
import com.recharge.backend.api.VerifyPaymentRequest
import com.recharge.backend.api.VerifyPaymentResponse

interface PaymentGatewayProvider {
    val providerName: String
    fun isConfigured(): Boolean
    fun createWalletOrder(userId: Long, request: CreatePaymentOrderRequest): CreatePaymentOrderResponse
    fun verifyWalletPayment(userId: Long, request: VerifyPaymentRequest): VerifyPaymentResponse
}
