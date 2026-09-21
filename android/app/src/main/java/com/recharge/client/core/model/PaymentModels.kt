package com.recharge.client.core.model

import java.math.BigDecimal

data class CreatePaymentOrderRequest(
    val amount: BigDecimal,
    val clientRequestId: String,
    val purpose: String = "ADD_MONEY",
    val rechargeMobileNumber: String? = null,
    val rechargeOperator: String? = null,
    val rechargeCircle: String? = null,
    val rechargePlanId: String? = null
)

data class PaymentOrderResponse(
    val provider: String = "razorpay",
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val keyId: String,
    val checkoutParams: Map<String, String> = emptyMap()
)

data class VerifyPaymentRequest(
    val provider: String = "razorpay",
    val paymentId: String? = null,
    val orderId: String? = null,
    val signature: String? = null
)

data class PaymentVerificationResponse(
    val status: String? = null,
    val balance: BigDecimal = BigDecimal.ZERO,
    val transactionId: String? = null,
    val rechargeStatus: String? = null,
    val amount: BigDecimal? = null,
    val commission: BigDecimal? = null,
    val walletDebitAmount: BigDecimal? = null,
    val message: String? = null,
    val currency: String = "INR"
)
