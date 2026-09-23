package com.recharge.backend.service

import com.recharge.backend.api.RentalAdminPayoutPageResponse
import com.recharge.backend.api.RentalAdminPayoutResponse
import com.recharge.backend.domain.RentalPayoutEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.RentalBookingRepository
import com.recharge.backend.repository.RentalPayoutRepository
import com.recharge.backend.repository.RentalVendorRepository
import com.recharge.backend.service.RoleAccessService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.RoundingMode

@Service
class AdminRentalOperationsService(
    private val payouts: RentalPayoutRepository,
    private val bookings: RentalBookingRepository,
    private val vendors: RentalVendorRepository,
    private val roleAccess: RoleAccessService
) {
    fun payouts(viewer: UserEntity, page: Int, size: Int, status: String?): RentalAdminPayoutPageResponse {
        roleAccess.requirePermission(viewer, "MANAGE_RENTAL_OPERATIONS")
        require(page >= 0) { "Page must be non-negative" }
        require(size in 1..50) { "Page size must be between 1 and 50" }
        val pageable = PageRequest.of(page, size)
        val normalizedStatus = status?.trim()?.uppercase()?.takeIf { it.isNotBlank() && it != "ALL" }
        val result = if (normalizedStatus == null) {
            payouts.findAllByOrderByCreatedAtDesc(pageable)
        } else {
            payouts.findAllByStatusOrderByCreatedAtDesc(normalizedStatus, pageable)
        }
        if (result.isEmpty) {
            return RentalAdminPayoutPageResponse(emptyList(), result.number, result.size, result.totalElements, result.totalPages, result.hasNext())
        }
        val bookingById = bookings.findAllByBookingIdIn(result.content.map { it.bookingId }).associateBy { it.bookingId }
        val vendorById = vendors.findAllById(result.content.map { it.vendorId }.distinct()).associateBy { requireNotNull(it.id) }
        return RentalAdminPayoutPageResponse(
            items = result.content.map { payout ->
                toResponse(payout, vendorById[ payout.vendorId ]?.businessName ?: vendorById[payout.vendorId]?.fullName ?: "Vendor")
            },
            page = result.number,
            size = result.size,
            totalItems = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext()
        )
    }

    private fun toResponse(payout: RentalPayoutEntity, vendorName: String): RentalAdminPayoutResponse =
        RentalAdminPayoutResponse(
            payoutId = payout.payoutId,
            bookingId = payout.bookingId,
            vendorId = payout.vendorId.toString(),
            vendorName = vendorName,
            grossAmount = payout.grossAmount.setScale(2, RoundingMode.HALF_UP),
            platformFeePercent = payout.platformFeePercent.setScale(2, RoundingMode.HALF_UP),
            platformFeeAmount = payout.platformFeeAmount.setScale(2, RoundingMode.HALF_UP),
            vendorNetAmount = payout.vendorNetAmount.setScale(2, RoundingMode.HALF_UP),
            status = payout.status,
            walletLedgerRef = payout.walletLedgerRef,
            failureReason = payout.failureReason,
            createdAt = payout.createdAt,
            updatedAt = payout.updatedAt,
            paidAt = payout.paidAt
        )
}
