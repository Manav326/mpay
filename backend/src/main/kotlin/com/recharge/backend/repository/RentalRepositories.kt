package com.recharge.backend.repository

import com.recharge.backend.domain.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.math.BigDecimal
import java.util.Optional

interface RentalVendorRepository : JpaRepository<RentalVendorEntity, Long> {
    fun findByUserId(userId: Long): Optional<RentalVendorEntity>
    fun findAllByStatusOrderByCreatedAtAsc(status: String): List<RentalVendorEntity>
    fun findAllByOrderByCreatedAtDesc(): List<RentalVendorEntity>
}

interface RentalDriverRepository : JpaRepository<RentalDriverEntity, Long> {
    fun findAllByVendorIdAndActiveTrueOrderByFullNameAsc(vendorId: Long): List<RentalDriverEntity>
}

interface RentalCarRepository : JpaRepository<RentalCarEntity, Long> {
    fun findAllByActiveTrueAndApprovalStatusAndVendorIdIsNotNullOrderByPricePerDayAsc(approvalStatus: String): List<RentalCarEntity>
    fun findAllByVendorIdOrderByIdDesc(vendorId: Long): List<RentalCarEntity>
    fun countByVendorId(vendorId: Long): Int
    fun existsByRegistrationNumberIgnoreCase(registrationNumber: String): Boolean
    fun existsByRegistrationNumberIgnoreCaseAndIdNot(registrationNumber: String, id: Long): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RentalCarEntity c where c.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<RentalCarEntity>
}

interface RentalBookingRepository : JpaRepository<RentalBookingEntity, Long> {
    fun findByBookingId(bookingId: String): Optional<RentalBookingEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from RentalBookingEntity b where b.bookingId = :bookingId")
    fun findByBookingIdForUpdate(@Param("bookingId") bookingId: String): Optional<RentalBookingEntity>

    fun findAllByBookingIdIn(bookingIds: Collection<String>): List<RentalBookingEntity>
    fun findByBookingIdAndUserId(bookingId: String, userId: Long): Optional<RentalBookingEntity>
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<RentalBookingEntity>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<RentalBookingEntity>
    fun findAllByStatusOrderByCreatedAtDesc(status: String, pageable: Pageable): Page<RentalBookingEntity>
    fun countByStatus(status: String): Long

    @Query("""
        select count(b) from RentalBookingEntity b
        where b.status = 'CONFIRMED'
          and b.startDate <= :now and b.endDate > :now
    """)
    fun countActive(@Param("now") now: LocalDateTime): Long

    @Query("select coalesce(sum(b.totalAmount), 0) from RentalBookingEntity b")
    fun sumTotalAmount(): BigDecimal

    @Query("select coalesce(sum(b.totalAmount), 0) from RentalBookingEntity b where b.status = 'CANCELLED'")
    fun sumCancelledAmount(): BigDecimal

    @Query("""
        select count(b) > 0 from RentalBookingEntity b
        where b.carId = :carId and b.status in :statuses
          and b.startDate < :endDate and b.endDate > :startDate
    """)
    fun existsOverlapping(
        @Param("carId") carId: Long,
        @Param("statuses") statuses: Collection<String>,
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): Boolean
}


interface RentalPaymentRepository : JpaRepository<RentalPaymentEntity, Long> {
    fun findByUserIdAndClientRequestId(userId: Long, clientRequestId: String): Optional<RentalPaymentEntity>
    fun findByBookingIdAndUserId(bookingId: String, userId: Long): Optional<RentalPaymentEntity>

    @Query("select coalesce(sum(p.amount), 0) from RentalPaymentEntity p where p.status = 'REFUNDED'")
    fun sumRefundedAmount(): BigDecimal

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RentalPaymentEntity p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<RentalPaymentEntity>
}


interface RentalPayoutRepository : JpaRepository<RentalPayoutEntity, Long> {
    fun findByBookingId(bookingId: String): Optional<RentalPayoutEntity>

    @Query("select p from RentalPayoutEntity p where p.vendorUserId = :vendorUserId order by p.createdAt desc")
    fun findAllByVendorUserIdOrderByCreatedAtDesc(@Param("vendorUserId") vendorUserId: Long): List<RentalPayoutEntity>

    @Query("select coalesce(sum(p.vendorNetAmount), 0) from RentalPayoutEntity p where p.status = 'PAID'")
    fun sumPaidVendorAmount(): BigDecimal

    @Query("select coalesce(sum(p.platformFeeAmount), 0) from RentalPayoutEntity p where p.status = 'PAID'")
    fun sumPlatformFeeAmount(): BigDecimal
}
