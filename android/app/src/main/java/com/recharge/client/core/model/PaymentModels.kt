package com.recharge.client.core.model

import java.math.BigDecimal

/** Request sent to the backend to create a Razorpay order. */
data class CreatePaymentOrderRequest(
    val amount: BigDecimal,
    val clientRequestId: String
)

data class PaymentOrderResponse(
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val keyId: String
)

/** Razorpay callback values sent back to the backend for verification. */
data class VerifyPaymentRequest(
    val razorpayPaymentId: String,
    val razorpayOrderId: String,
    val razorpaySignature: String
)

/** The backend is intentionally allowed to evolve its response body. */
data class PaymentVerificationResponse(
    val status: String? = null,
    val message: String? = null
)
