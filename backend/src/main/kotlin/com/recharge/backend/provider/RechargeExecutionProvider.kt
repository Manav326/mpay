package com.recharge.backend.provider

interface RechargeExecutionProvider {
    val providerName: String
    fun isConfigured(): Boolean = true
    fun supportsOperator(operator: String): Boolean = true

    /** Resolve provider-specific data before any wallet reservation or external submission. */
    fun prepareBeforeSubmission(request: ProviderRechargeRequest): ProviderRechargeRequest = request

    /** Validate provider prerequisites before any wallet reservation or external submission. */
    fun validateBeforeSubmission(request: ProviderRechargeRequest) {}

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
