package com.recharge.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.withdrawal")
data class WithdrawalProperties(
    val providerOrder: String = "razorpay,payu"
)

@ConfigurationProperties(prefix = "app.razorpay.payout")
data class RazorpayPayoutProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://api.razorpay.com/v1",
    val keyId: String = "",
    val keySecret: String = "",
    val accountNumber: String = "",
    val contactId: String = "",
    val purpose: String = "payout",
    val webhookSecret: String = ""
)

@ConfigurationProperties(prefix = "app.payu.payout")
data class PayUPayoutProperties(
    val enabled: Boolean = false,
    val environment: String = "test",
    val authBaseUrl: String = "https://uat-accounts.payu.in",
    val apiBaseUrl: String = "https://uatoneapi.payu.in",
    val clientId: String = "",
    val clientSecret: String = "",
    val payoutMerchantId: String = "",
    val scope: String = "create_payout_transactions",
    val webhookAuthorization: String = ""
)
