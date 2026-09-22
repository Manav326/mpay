package com.recharge.backend.service

import com.recharge.backend.domain.RentalBookingEntity
import com.recharge.backend.domain.RentalPayoutEntity
import com.recharge.backend.repository.RentalCarRepository
import com.recharge.backend.repository.RentalPayoutRepository
import com.recharge.backend.repository.RentalVendorRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Service
class RentalPayoutService(
    private val payouts: RentalPayoutRepository,
    private val cars: RentalCarRepository,
    private val vendors: RentalVendorRepository,
    private val wallet: WalletService,
    @Value("\${app.rental.platform-fee-percent:10.00}") private val platformFeePercent: BigDecimal
) {
    @Transactional
    fun settleCompletedBooking(booking: RentalBookingEntity): RentalPayoutEntity {
        check(booking.status == "COMPLETED") { "Only completed rental bookings can be settled" }
        val existing = payouts.findByBookingId(booking.bookingId).orElse(null)
        if (existing != null) {
            if (existing.status == "PAID") return existing
            return settle(existing)
        }

        val car = cars.findById(booking.carId).orElseThrow { IllegalArgumentException("Rental car not found") }
        val vendorId = requireNotNull(car.vendorId) { "Rental vendor not found for booking" }
        val vendor = vendors.findById(vendorId).orElseThrow { IllegalArgumentException("Rental vendor not found") }
        val gross = booking.totalAmount.setScale(2, RoundingMode.HALF_UP)
        val feeRate = platformFeePercent.max(BigDecimal.ZERO).min(BigDecimal("100.00"))
        val fee = gross.multiply(feeRate).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
        val net = gross.subtract(fee).setScale(2, RoundingMode.HALF_UP)
        val now = Instant.now()
        val payout = payouts.save(RentalPayoutEntity(
            payoutId = "RNPY-" + UUID.randomUUID().toString().replace("-", "").take(20).uppercase(),
            bookingId = booking.bookingId,
            vendorId = vendorId,
            vendorUserId = vendor.userId,
            grossAmount = gross,
            platformFeePercent = feeRate,
            platformFeeAmount = fee,
            vendorNetAmount = net,
            status = "PENDING",
            createdAt = now,
            updatedAt = now
        ))
        return settle(payout)
    }

    @Transactional
    fun settle(payout: RentalPayoutEntity): RentalPayoutEntity {
        if (payout.status == "PAID") return payout
        check(payout.status == "PENDING") { "Rental payout is not payable" }
        val ledgerRef = "RENTAL_PAYOUT:" + payout.payoutId
        wallet.credit(
            userId = payout.vendorUserId,
            amount = payout.vendorNetAmount,
            externalRef = ledgerRef,
            referenceType = "RENTAL_PAYOUT",
            referenceId = payout.bookingId,
            description = "Rental vendor payout"
        )
        payout.walletLedgerRef = ledgerRef
        payout.status = "PAID"
        payout.paidAt = Instant.now()
        payout.updatedAt = Instant.now()
        return payouts.save(payout)
    }
}
