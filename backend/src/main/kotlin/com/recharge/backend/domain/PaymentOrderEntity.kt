package com.recharge.backend.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "payment_orders")
class PaymentOrderEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "client_request_id", nullable = false, unique = true, length = 80) var clientRequestId: String = "",
    @Column(name = "user_id", nullable = false) var userId: Long = 0,
    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 100) var razorpayOrderId: String = "",
    @Column(name = "provider_name", nullable = false, length = 40) var providerName: String = "razorpay",
    @Column(nullable = false, length = 30) var purpose: String = "ADD_MONEY",
    @Column(name = "recharge_mobile_number", length = 15) var rechargeMobileNumber: String? = null,
    @Column(name = "recharge_operator", length = 30) var rechargeOperator: String? = null,
    @Column(name = "recharge_circle", length = 100) var rechargeCircle: String? = null,
    @Column(name = "recharge_plan_id", length = 150) var rechargePlanId: String? = null,
    @Column(nullable = false, precision = 19, scale = 2) var amount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, length = 3) var currency: String = "INR",
    @Column(nullable = false, length = 30) var status: String = "CREATED",
    @Column(name = "razorpay_payment_id", unique = true, length = 100) var razorpayPaymentId: String? = null,
    @Column(name = "razorpay_signature", length = 255) var razorpaySignature: String? = null,
    @Column(nullable = false) var createdAt: Instant = Instant.now(),
    var verifiedAt: Instant? = null,
    @Version var version: Long? = null
)
