package com.recharge.backend.provider

import java.math.BigDecimal

interface RechargeProvider {
    fun detectOperator(mobileNumber: String): OperatorResult
    fun getPlans(mobileNumber: String, operator: String, circle: String): List<RechargePlan>
    fun recharge(userId: Long, mobileNumber: String, planId: String): ProviderRechargeResult
}

data class OperatorResult(
    val mobileNumber: String,
    val operator: String,
    val providerOperator: String,
    val circle: String,
    val type: String?,
    val providerOrderId: String?
)

data class RechargePlan(
    val id: String,
    val amount: BigDecimal,
    val validity: String?,
    val description: String?,
    val providerReference: String? = null,
    val providerOrderId: String? = null,
    val providerLogDescription: String? = null
)

data class ProviderRechargeResult(
    val status: String,
    val providerReference: String? = null,
    val message: String? = null
)
