package com.recharge.backend.repository

import com.recharge.backend.domain.RentalCarReviewEntity
import com.recharge.backend.domain.RentalVendorReviewEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RentalVendorReviewRepository : JpaRepository<RentalVendorReviewEntity, Long> {
    fun findAllByVendorIdOrderByCreatedAtDesc(vendorId: Long): List<RentalVendorReviewEntity>
}

interface RentalCarReviewRepository : JpaRepository<RentalCarReviewEntity, Long> {
    fun findAllByCarIdOrderByCreatedAtDesc(carId: Long): List<RentalCarReviewEntity>
}
