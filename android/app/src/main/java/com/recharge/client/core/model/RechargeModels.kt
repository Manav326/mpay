package com.recharge.client.core.model

import java.math.BigDecimal

/** Request used to execute a recharge against the authenticated user's wallet. */
data class RechargeRequest(
    val mobileNumber: String,
    val operator: String,
    val circle: String,
    val planId: String,
    val clientRequestId: String
)

data class RechargeResponse(
    val transactionId: String,
    val status: String,
    val amount: BigDecimal,
    val commission: BigDecimal,
    val walletDebitAmount: BigDecimal,
    val walletBalance: BigDecimal
)

data class RechargeTransactionStatusResponse(
    val transactionId: String,
    val clientRequestId: String,
    val mobileNumber: String,
    val operator: String,
    val circle: String,
    val planId: String,
    val planDescription: String? = null,
    val planValidity: String? = null,
    val amount: BigDecimal,
    val walletDebitAmount: BigDecimal,
    val clientCommission: BigDecimal = BigDecimal.ZERO,
    val status: String,
    val provider: String,
    val providerReference: String? = null,
    val providerOrderId: String? = null,
    val walletLedgerRef: String? = null,
    val companyCommission: BigDecimal = BigDecimal.ZERO,
    val message: String? = null,
    val completedAt: String? = null,
    val walletBalance: BigDecimal
)
