package com.recharge.backend.service

import com.recharge.backend.domain.RentalPaymentEntity
import com.recharge.backend.repository.RentalPaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class RentalPaymentService(
    private val payments: RentalPaymentRepository,
    private val wallet: WalletService
) {
    @Transactional
    fun pay(
        userId: Long,
        bookingId: String,
        amount: BigDecimal,
        method: String,
        clientRequestId: String
    ): RentalPaymentEntity {
        require(clientRequestId.isNotBlank()) { "Client request id is required" }
        require(amount.signum() > 0) { "Rental payment amount must be greater than zero" }

        val existing = payments.findByUserIdAndClientRequestId(userId, clientRequestId).orElse(null)
        if (existing != null) {
            require(existing.bookingId == bookingId) { "Client request id is already used for another rental booking" }
            require(existing.amount.compareTo(amount) == 0) { "Client request id is already used for a different amount" }
            require(existing.method.equals(method, true)) { "Client request id is already used with a different payment method" }
            return existing
        }

        require(method.equals("WALLET", true)) { "This rental payment flow currently supports wallet payment" }
        val normalizedMethod = method.trim().uppercase()
        val paymentId = "RNP-" + UUID.randomUUID().toString().replace("-", "").take(20).uppercase()
        val ledgerRef = "RENTAL:" + bookingId
        val now = Instant.now()
        val payment = payments.save(RentalPaymentEntity(
            paymentId = paymentId, bookingId = bookingId, userId = userId, amount = amount,
            method = normalizedMethod, provider = "INTERNAL_WALLET",
            clientRequestId = clientRequestId.trim(), status = "PENDING",
            walletLedgerRef = ledgerRef, createdAt = now, updatedAt = now
        ))

        wallet.reserve(userId, amount)
        wallet.finalizeReservedDebit(
            userId = userId, amount = amount, externalRef = ledgerRef, referenceId = bookingId,
            referenceType = "RENTAL_PAYMENT", description = "Car rental payment", transactionType = "DEBIT"
        )
        payment.status = "PAID"
        payment.providerTransactionId = ledgerRef
        payment.updatedAt = Instant.now()
        return payments.save(payment)
    }

    @Transactional
    fun refund(payment: RentalPaymentEntity): RentalPaymentEntity {
        val locked = payments.findByIdForUpdate(requireNotNull(payment.id)).orElseThrow { IllegalArgumentException("Rental payment not found") }
        if (locked.status == "REFUNDED") return locked
        check(locked.status == "PAID") { "Only paid rental payments can be refunded" }
        wallet.credit(
            userId = locked.userId, amount = locked.amount, externalRef = "RENTAL_REFUND:" + locked.bookingId,
            referenceType = "RENTAL_REFUND", referenceId = locked.bookingId, description = "Car rental refund"
        )
        locked.status = "REFUNDED"
        locked.updatedAt = Instant.now()
        return payments.save(locked)
    }
}
