package com.recharge.backend.repository

import com.recharge.backend.domain.RentalBookingEntity
import com.recharge.backend.domain.RentalCarEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RentalCarRepository : JpaRepository<RentalCarEntity, Long> {
    fun findAllByActiveTrueOrderByPricePerDayAsc(): List<RentalCarEntity>
}

interface RentalBookingRepository : JpaRepository<RentalBookingEntity, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<RentalBookingEntity>

    @Query("""
        select count(b) > 0 from RentalBookingEntity b
        where b.carId = :carId
          and b.status in :statuses
          and b.startDate < :endDate
          and b.endDate > :startDate
    """)
    fun existsOverlapping(
        @Param("carId") carId: Long,
        @Param("statuses") statuses: Collection<String>,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Boolean
}
