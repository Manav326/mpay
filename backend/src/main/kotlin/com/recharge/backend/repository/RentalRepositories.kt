package com.recharge.backend.repository

import com.recharge.backend.domain.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
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
    fun findByBookingIdAndUserId(bookingId: String, userId: Long): Optional<RentalBookingEntity>
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<RentalBookingEntity>

    @Query("""
        select count(b) > 0 from RentalBookingEntity b
        where b.carId = :carId and b.status in :statuses
          and b.startDate < :endDate and b.endDate > :startDate
    """)
    fun existsOverlapping(
        @Param("carId") carId: Long,
        @Param("statuses") statuses: Collection<String>,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Boolean
}


interface RentalPaymentRepository : JpaRepository<RentalPaymentEntity, Long> {
    fun findByUserIdAndClientRequestId(userId: Long, clientRequestId: String): Optional<RentalPaymentEntity>
    fun findByBookingIdAndUserId(bookingId: String, userId: Long): Optional<RentalPaymentEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RentalPaymentEntity p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<RentalPaymentEntity>
}
