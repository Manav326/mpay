package com.recharge.backend.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "rental_cars")
class RentalCarEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, length = 120) var name: String = "",
    @Column(nullable = false, length = 50) var category: String = "",
    @Column(nullable = false) var seats: Int = 5,
    @Column(nullable = false, length = 30) var transmission: String = "Manual",
    @Column(name = "price_per_day", nullable = false, precision = 19, scale = 2) var pricePerDay: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false) var active: Boolean = true
)

@Entity
@Table(name = "rental_bookings")
class RentalBookingEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "booking_id", nullable = false, unique = true, length = 40) var bookingId: String = "",
    @Column(name = "user_id", nullable = false) var userId: Long = 0,
    @Column(name = "car_id", nullable = false) var carId: Long = 0,
    @Column(name = "pickup_location", nullable = false, length = 300) var pickupLocation: String = "",
    @Column(name = "drop_location", nullable = false, length = 300) var dropLocation: String = "",
    @Column(name = "start_date", nullable = false) var startDate: LocalDate = LocalDate.now(),
    @Column(name = "end_date", nullable = false) var endDate: LocalDate = LocalDate.now().plusDays(1),
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2) var totalAmount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, length = 30) var status: String = "CONFIRMED",
    @Column(name = "wallet_ledger_ref", length = 150) var walletLedgerRef: String? = null,
    @Column(nullable = false) var createdAt: Instant = Instant.now(),
    @Column(nullable = false) var updatedAt: Instant = Instant.now()
)
