package com.recharge.backend.service

import java.math.BigDecimal

interface WithdrawalProvider {
    val providerName: String
    fun isConfigured(): Boolean
    fun initiate(request: WithdrawalProviderRequest): WithdrawalProviderResult
}

data class WithdrawalProviderRequest(
    val withdrawalId: String,
    val amount: BigDecimal,
    val upiId: String,
    val customerName: String,
    val customerEmail: String,
    val customerMobile: String
)

data class WithdrawalProviderResult(
    val status: String,
    val providerReference: String? = null,
    val message: String? = null,
    val providerStatus: String? = null
)
