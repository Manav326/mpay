package com.recharge.backend.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "rental_vendors")
class RentalVendorEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name="user_id", nullable=false, unique=true) var userId: Long = 0,
    @Column(name="vendor_type", nullable=false, length=20) var vendorType: String = "INDIVIDUAL",
    @Column(nullable=false, length=30) var status: String = "PENDING",
    @Column(name="full_name", nullable=false, length=120) var fullName: String = "",
    @Column(name="business_name", length=160) var businessName: String? = null,
    @Column(nullable=false, length=300) var address: String = "",
    @Column(nullable=false, length=100) var city: String = "",
    @Column(nullable=false, length=100) var state: String = "",
    @Column(name="pin_code", nullable=false, length=10) var pinCode: String = "",
    @Column(name="pan_number", length=20) var panNumber: String? = null,
    @Column(name="payout_upi_id", length=254) var payoutUpiId: String? = null,
    @Column(name="bank_account_number", length=64) var bankAccountNumber: String? = null,
    @Column(name="bank_ifsc", length=20) var bankIfsc: String? = null,
    @Column(name="created_at", nullable=false) var createdAt: Instant = Instant.now(),
    @Column(name="updated_at", nullable=false) var updatedAt: Instant = Instant.now(),
    @Column(name="rejection_reason", length=500) var rejectionReason: String? = null
)

@Entity
@Table(name = "rental_drivers")
class RentalDriverEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name="vendor_id", nullable=false) var vendorId: Long = 0,
    @Column(name="full_name", nullable=false, length=120) var fullName: String = "",
    @Column(nullable=false, length=20) var mobile: String = "",
    @Column(name="license_number", nullable=false, length=64) var licenseNumber: String = "",
    @Column(name="license_expiry", nullable=false) var licenseExpiry: LocalDate = LocalDate.now(),
    @Column(length=300) var address: String? = null,
    @Column(nullable=false) var active: Boolean = true,
    @Column(name="rejection_reason", length=500) var rejectionReason: String? = null,
    @Column(name="created_at", nullable=false) var createdAt: Instant = Instant.now(),
    @Column(name="updated_at", nullable=false) var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "rental_cars")
class RentalCarEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, length = 120) var name: String = "",
    @Column(nullable = false, length = 50) var category: String = "",
    @Column(nullable = false) var seats: Int = 5,
    @Column(nullable = false, length = 30) var transmission: String = "Manual",
    @Column(name = "price_per_day", nullable = false, precision = 19, scale = 2) var pricePerDay: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false) var active: Boolean = true,
    @Column(name="vendor_id") var vendorId: Long? = null,
    @Column(name="driver_id") var driverId: Long? = null,
    @Column(name="registration_number", length=32) var registrationNumber: String? = null,
    @Column(length=80) var make: String? = null,
    @Column(length=80) var model: String? = null,
    @Column(length=80) var variant: String? = null,
    @Column(name="manufacturing_year") var manufacturingYear: Int? = null,
    @Column(name="fuel_type", length=30) var fuelType: String? = null,
    @Column(name="registration_year") var registrationYear: Int? = null,
    @Column(name="pickup_address", length=300) var pickupAddress: String? = null,
    @Column(length=100) var city: String? = null,
    @Column(length=100) var state: String? = null,
    @Column(name="image_url", length=500) var imageUrl: String? = null,
    @Column(name="approval_status", nullable=false, length=30) var approvalStatus: String = "DRAFT",
    @Column(name="rejection_reason", length=500) var rejectionReason: String? = null
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
    @Column(name = "payment_method", nullable=false, length=30) var paymentMethod: String = "WALLET",
    @Column(nullable = false) var createdAt: Instant = Instant.now(),
    @Column(nullable = false) var updatedAt: Instant = Instant.now()
)


@Entity
@Table(name = "rental_vendor_review_history")
class RentalVendorReviewEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "vendor_id", nullable = false) var vendorId: Long = 0,
    @Column(nullable = false, length = 30) var action: String = "",
    @Column(length = 500) var reason: String? = null,
    @Column(name = "actor_user_id", nullable = false) var actorUserId: Long = 0,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "rental_car_review_history")
class RentalCarReviewEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "car_id", nullable = false) var carId: Long = 0,
    @Column(nullable = false, length = 30) var action: String = "",
    @Column(length = 500) var reason: String? = null,
    @Column(name = "actor_user_id", nullable = false) var actorUserId: Long = 0,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
