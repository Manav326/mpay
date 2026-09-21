package com.recharge.backend.provider

import java.math.BigDecimal

interface OperatorDetectionProvider {
    val providerName: String
    fun isConfigured(): Boolean = true
    fun detectOperator(mobileNumber: String): OperatorResult
}

interface RechargeProvider : OperatorDetectionProvider {
    fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan>
    fun recharge(userId: Long, mobileNumber: String, planId: String): ProviderRechargeResult
}

data class OperatorResult(
    val mobileNumber: String,
    val operator: String,
    val providerOperator: String,
    val circle: String,
    val providerCircle: String? = null,
    val type: String?,
    val providerOrderId: String?,
    val status: String = "UNKNOWN",
    val pending: Boolean = false,
    val message: String? = null,
    val messageCode: String? = null
)

data class RechargePlan(
    val id: String,
    val amount: BigDecimal,
    val validity: String?,
    val description: String?,
    val providerReference: String? = null,
    val providerOrderId: String? = null,
    val providerLogDescription: String? = null,
    val providerMetadata: Map<String, String> = emptyMap()
)

data class ProviderRechargeResult(
    val status: String,
    val providerReference: String? = null,
    val message: String? = null
)
