package com.recharge.backend.service

import com.recharge.backend.api.RechargeRequest
import com.recharge.backend.api.VerifyPaymentResponse
import com.recharge.backend.domain.PaymentOrderEntity
import com.recharge.backend.repository.RechargeTransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentSettlementService(
    private val walletService: WalletService,
    private val rechargeService: RechargeService,
    private val rechargeRepository: RechargeTransactionRepository
) {
    fun responseForCaptured(userId: Long, order: PaymentOrderEntity): VerifyPaymentResponse {
        if (order.purpose.equals("RECHARGE", true)) {
            val recharge = rechargeRepository.findByClientRequestIdAndUserId(order.clientRequestId, userId).orElse(null)
            if (recharge != null) {
                val wallet = walletService.getWalletSnapshot(userId)
                return VerifyPaymentResponse(
                    status = "CAPTURED",
                    balance = wallet.balance,
                    availableBalance = wallet.availableBalance,
                    transactionId = recharge.transactionId,
                    rechargeStatus = recharge.status,
                    amount = recharge.amount,
                    commission = recharge.clientCommission,
                    walletDebitAmount = recharge.walletDebitAmount,
                    message = recharge.message
                )
            }
        }
        return VerifyPaymentResponse(status = "CAPTURED", balance = walletService.getBalance(userId))
    }

    @Transactional
    fun settleCaptured(userId: Long, order: PaymentOrderEntity, externalPaymentReference: String): VerifyPaymentResponse {
        return when (order.purpose.uppercase()) {
            "RECHARGE" -> {
                val mobile = order.rechargeMobileNumber ?: throw IllegalArgumentException("Recharge payment is missing mobile number")
                val operator = order.rechargeOperator ?: throw IllegalArgumentException("Recharge payment is missing operator")
                val circle = order.rechargeCircle ?: throw IllegalArgumentException("Recharge payment is missing circle")
                val planId = order.rechargePlanId ?: throw IllegalArgumentException("Recharge payment is missing plan id")

                walletService.credit(
                    userId = userId,
                    amount = order.amount,
                    externalRef = "PAYMENT:" + order.providerName.uppercase() + ":" + externalPaymentReference,
                    referenceType = "ADD_MONEY",
                    referenceId = order.clientRequestId,
                    description = "Gateway funding for mobile recharge"
                )

                val recharge = rechargeService.recharge(
                    userId = userId,
                    request = RechargeRequest(
                        mobileNumber = mobile,
                        operator = operator,
                        circle = circle,
                        planId = planId,
                        clientRequestId = order.clientRequestId
                    )
                )

                VerifyPaymentResponse(
                    status = "CAPTURED",
                    balance = recharge.walletBalance,
                    availableBalance = walletService.getAvailableBalance(userId),
                    transactionId = recharge.transactionId,
                    rechargeStatus = recharge.status,
                    amount = recharge.amount,
                    commission = recharge.commission,
                    walletDebitAmount = recharge.walletDebitAmount
                )
            }

            else -> {
                val balance = walletService.credit(
                    userId = userId,
                    amount = order.amount,
                    externalRef = "PAYMENT:" + order.providerName.uppercase() + ":" + externalPaymentReference,
                    referenceType = "ADD_MONEY",
                    referenceId = externalPaymentReference,
                    description = "Wallet top-up via " + order.providerName.uppercase()
                )
                VerifyPaymentResponse(status = "CAPTURED", balance = balance, availableBalance = walletService.getAvailableBalance(userId))
            }
        }
    }
}
