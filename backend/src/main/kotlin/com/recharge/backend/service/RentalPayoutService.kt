package com.recharge.backend.service

import com.recharge.backend.domain.RentalBookingEntity
import com.recharge.backend.domain.RentalPayoutEntity
import com.recharge.backend.repository.RentalCarRepository
import com.recharge.backend.repository.RentalPayoutRepository
import com.recharge.backend.repository.RentalVendorRepository
import com.recharge.backend.repository.RentalBookingRepository
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
    private val bookings: RentalBookingRepository,
    private val cars: RentalCarRepository,
    private val vendors: RentalVendorRepository,
    private val wallet: WalletService,
    @Value("\${app.rental.platform-fee-percent:10.00}") private val platformFeePercent: BigDecimal
) {
    fun findByVendorUserId(vendorUserId: Long): List<RentalPayoutEntity> =
        payouts.findAllByVendorUserIdOrderByCreatedAtDesc(vendorUserId)

    fun totalPaidVendorAmount(): BigDecimal = payouts.sumPaidVendorAmount().setScale(2, RoundingMode.HALF_UP)

    fun totalPlatformFeeAmount(): BigDecimal = payouts.sumPlatformFeeAmount().setScale(2, RoundingMode.HALF_UP)

    @Transactional
    fun settleCompletedBooking(booking: RentalBookingEntity): RentalPayoutEntity {
        val lockedBooking = bookings.findByBookingIdForUpdate(booking.bookingId)
            .orElseThrow { IllegalArgumentException("Rental booking not found") }
        check(lockedBooking.status == "COMPLETED") { "Only completed rental bookings can be settled" }
        val existing = payouts.findByBookingId(lockedBooking.bookingId).orElse(null)
        if (existing != null) {
            if (existing.status == "PAID") return existing
            return settle(existing)
        }

        val car = cars.findById(lockedBooking.carId).orElseThrow { IllegalArgumentException("Rental car not found") }
        val vendorId = requireNotNull(car.vendorId) { "Rental vendor not found for booking" }
        val vendor = vendors.findById(vendorId).orElseThrow { IllegalArgumentException("Rental vendor not found") }
        val gross = booking.totalAmount.setScale(2, RoundingMode.HALF_UP)
        val feeRate = platformFeePercent.max(BigDecimal.ZERO).min(BigDecimal("100.00"))
        val fee = gross.multiply(feeRate).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
        val net = gross.subtract(fee).setScale(2, RoundingMode.HALF_UP)
        val now = Instant.now()
        val payout = payouts.save(RentalPayoutEntity(
            payoutId = "RNPY-" + UUID.randomUUID().toString().replace("-", "").take(20).uppercase(),
            bookingId = lockedBooking.bookingId,
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
