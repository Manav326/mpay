package com.recharge.backend.service

import com.recharge.backend.repository.PaymentOrderRepository
import com.recharge.backend.repository.RentalBookingRepository
import com.recharge.backend.repository.RechargeTransactionRepository
import com.recharge.backend.repository.WalletRepository
import com.recharge.backend.repository.WalletTransactionRepository
import com.recharge.backend.repository.WalletWithdrawalRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class SupportAiCustomerContextService(
    private val wallets: WalletRepository,
    private val walletTransactions: WalletTransactionRepository,
    private val recharges: RechargeTransactionRepository,
    private val withdrawals: WalletWithdrawalRepository,
    private val payments: PaymentOrderRepository,
    private val bookings: RentalBookingRepository
) {
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun buildContext(customerUserId: Long, question: String): String? {
        if (!needsCustomerContext(question)) return null

        val wallet = wallets.findByUserId(customerUserId).orElse(null)
        val latestWalletTransaction = walletTransactions.findTop10ByUserIdOrderByCreatedAtDesc(customerUserId).firstOrNull()
        val latestRecharge = recharges.findTopByUserIdOrderByCreatedAtDesc(customerUserId)
        val latestWithdrawal = withdrawals.findTop20ByUserIdOrderByCreatedAtDesc(customerUserId).firstOrNull()
        val latestPayment = payments.findTopByUserIdOrderByCreatedAtDesc(customerUserId)
        val latestBooking = bookings.findAllByUserIdOrderByCreatedAtDesc(customerUserId, PageRequest.of(0, 1)).content.firstOrNull()

        return buildString {
            appendLine("AUTHENTICATED CUSTOMER CONTEXT. These are backend facts for the signed-in customer only.")
            wallet?.let {
                appendLine("Current wallet balance: " + it.balance)
                appendLine("Reserved wallet balance: " + it.reservedBalance)
            }
            latestWalletTransaction?.let {
                appendLine("Latest wallet transaction: type=" + it.type + "; amount=" + it.amount + "; status=" + it.status + "; referenceType=" + (it.referenceType ?: "—") + "; description=" + (it.description ?: "—") + "; time=" + format(it.createdAt))
            }
            latestRecharge?.let {
                appendLine("Latest recharge: operator=" + it.operator + "; amount=" + it.amount + "; status=" + it.status + "; provider=" + it.providerName + "; message=" + (it.message ?: "—") + "; time=" + format(it.createdAt))
            }
            latestWithdrawal?.let {
                appendLine("Latest withdrawal: amount=" + it.amount + "; status=" + it.status + "; provider=" + it.providerName + "; providerStatus=" + (it.providerStatus ?: "—") + "; failureReason=" + (it.failureReason ?: "—") + "; created=" + format(it.createdAt) + "; completed=" + (it.completedAt?.let(::format) ?: "—"))
            }
            latestPayment?.let {
                appendLine("Latest add-money/payment order: provider=" + it.providerName + "; purpose=" + it.purpose + "; amount=" + it.amount + "; currency=" + it.currency + "; status=" + it.status + "; created=" + format(it.createdAt) + "; verified=" + (it.verifiedAt?.let(::format) ?: "—"))
            }
            latestBooking?.let {
                appendLine("Latest rental booking: bookingId=" + it.bookingId + "; status=" + it.status + "; amount=" + it.totalAmount + "; paymentMethod=" + it.paymentMethod + "; start=" + it.startDate + "; end=" + it.endDate + "; created=" + format(it.createdAt))
            }
            appendLine("Do not infer success, failure, refund, or completion beyond these explicit statuses.")
        }
    }

    private fun needsCustomerContext(question: String): Boolean {
        val q = question.lowercase(Locale.ROOT)
        val keywords = listOf(
            "balance", "wallet", "add money", "payment", "payu", "razorpay", "transaction",
            "recharge", "plan", "withdraw", "withdrawal", "refund", "booking", "rental", "car",
            "money", "credited", "credit", "debited", "debit", "pending", "failed", "success",
            "status", "पैसा", "वॉलेट", "रिचार्ज", "भुगतान", "निकासी", "रिफंड", "बुकिंग",
            "पेंडिंग", "फेल", "सफल"
        )
        return keywords.any(q::contains)
    }

    private fun format(value: Instant): String =
        formatter.format(value.atOffset(ZoneOffset.UTC))
}
