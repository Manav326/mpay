package com.recharge.backend.provider

interface RechargeExecutionProvider {
    val providerName: String
    fun supportsOperator(operator: String): Boolean = true

    fun recharge(request: ProviderRechargeRequest): ProviderRechargeResult

    fun getStatus(transactionReference: String): ProviderRechargeResult? = null
}

data class ProviderRechargeRequest(
    val transactionId: String,
    val mobileNumber: String,
    val operator: String,
    val circle: String,
    val plan: RechargePlan
)
