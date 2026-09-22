package com.recharge.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.payu")
data class PayUProperties(
    val environment: String = "test",
    val authBaseUrl: String = "https://uat-accounts.payu.in",
    val nbcBaseUrl: String = "https://bbps-sb.payu.in",
    val clientId: String = "",
    val clientSecret: String = "",
    val scope: String = "read_plans",
    val agentId: String = "",
    val operatorCirclePath: String = "/payu-nbc/v2/nbc/getOperatorAndCircleInfo",
    val plansPath: String = "/payu-nbc/v3/nbc/getRechargePlans",
    val customPlansPath: String = "/payu-nbc/v3/nbc/getCustomizedRechargePlans",
    val operatorCodeMappings: Map<String, String> = emptyMap(),
    val circleCodeMappings: Map<String, String> = emptyMap(),
    val billPaymentPath: String = "/payu-nbc-int/v1/nbc/billpaymentrequest",
    val statusPath: String = "/payu-nbc/v2/nbc/status/billpayment",
    val paymentMode: String = "WALLET",
    val walletName: String = "PAYU",
    val pgName: String = "other",
    val initiatingChannel: String = "AGT",
    val pgKey: String = "",
    val pgSalt: String = "",
    val merchantKey: String = "",
    val merchantSalt: String = "",
    val pgVerifyUrl: String = "https://test.payu.in/merchant/postservice?form=2",
    val pgSuccessUrl: String = "https://cbjs.payu.in/sdk/success",
    val pgFailureUrl: String = "https://cbjs.payu.in/sdk/failure",
    val pgProduction: Boolean = false,
    val connectTimeoutMs: Long = 10000,
    val readTimeoutMs: Long = 30000
) {
    fun effectivePgKey(): String = pgKey.ifBlank { merchantKey }

    fun effectivePgSalt(): String = pgSalt.ifBlank { merchantSalt }
}
