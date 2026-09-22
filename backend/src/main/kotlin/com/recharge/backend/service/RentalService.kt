package com.recharge.backend.service

import com.recharge.backend.api.*
import com.recharge.backend.domain.*
import com.recharge.backend.repository.*
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class RentalService(
    private val vendors: RentalVendorRepository,
    private val drivers: RentalDriverRepository,
    private val cars: RentalCarRepository,
    private val bookings: RentalBookingRepository,
    private val users: UserRepository,
    private val wallet: WalletService
) {
    fun vendor(userId: Long): RentalVendorResponse {
        val vendor = vendors.findByUserId(userId).orElse(null)
        return vendor?.let {
            RentalVendorResponse(it.id.toString(), it.status, it.vendorType, it.fullName, it.businessName, it.city, it.state, cars.countByVendorId(requireNotNull(it.id)), it.address, it.pinCode, it.panNumber, it.payoutUpiId, it.bankAccountNumber, it.bankIfsc, it.rejectionReason, it.createdAt)
        } ?: RentalVendorResponse(null, "NOT_ONBOARDED", null, null, null, null, null, 0)
    }

    @Transactional
    fun onboardVendor(userId: Long, request: RentalVendorOnboardingRequest): RentalVendorResponse {
        val existing = vendors.findByUserId(userId)
        if (existing.isPresent) {
            val current = existing.get()
            if (current.status != "REJECTED") return vendor(userId)
            current.vendorType = request.vendorType.trim().uppercase()
            require(current.vendorType in setOf("INDIVIDUAL", "BUSINESS")) { "Vendor type must be INDIVIDUAL or BUSINESS" }
            current.fullName = request.fullName.trim()
            current.businessName = request.businessName?.trim()?.takeIf { it.isNotBlank() }
            current.address = request.address.trim()
            current.city = request.city.trim()
            current.state = request.state.trim()
            current.pinCode = request.pinCode.trim()
            current.panNumber = request.panNumber?.trim()?.uppercase()
            current.payoutUpiId = request.payoutUpiId?.trim()
            current.bankAccountNumber = request.bankAccountNumber?.trim()
            current.bankIfsc = request.bankIfsc?.trim()?.uppercase()
            current.status = "PENDING"
            current.rejectionReason = null
            current.updatedAt = Instant.now()
            vendors.save(current)
            return vendor(userId)
        }
        val type = request.vendorType.trim().uppercase()
        require(type in setOf("INDIVIDUAL", "BUSINESS")) { "Vendor type must be INDIVIDUAL or BUSINESS" }
        val now = Instant.now()
        val saved = vendors.save(
            RentalVendorEntity(
                userId = userId,
                vendorType = type,
                status = "PENDING",
                fullName = request.fullName.trim(),
                businessName = request.businessName?.trim()?.takeIf { it.isNotBlank() },
                address = request.address.trim(),
                city = request.city.trim(),
                state = request.state.trim(),
                pinCode = request.pinCode.trim(),
                panNumber = request.panNumber?.trim()?.uppercase(),
                payoutUpiId = request.payoutUpiId?.trim(),
                bankAccountNumber = request.bankAccountNumber?.trim(),
                bankIfsc = request.bankIfsc?.trim()?.uppercase(),
                createdAt = now,
                updatedAt = now
            )
        )
        return vendor(userId)
    }

    fun vendorCars(userId: Long): List<RentalCarResponse> {
        val vendor = verifiedVendor(userId)
        return cars.findAllByVendorIdOrderByIdDesc(requireNotNull(vendor.id)).map(::toCarResponse)
    }

    @Transactional
    fun onboardVehicle(userId: Long, request: RentalVehicleOnboardingRequest): RentalCarResponse {
        val vendor = verifiedVendor(userId)
        val vendorId = requireNotNull(vendor.id)
        require(request.seats in 1..20) { "Seats must be between 1 and 20" }
        require(request.pricePerDay > BigDecimal.ZERO) { "Price per day must be greater than zero" }
        require(!cars.existsByRegistrationNumberIgnoreCase(request.registrationNumber.trim())) { "A vehicle with this registration number already exists" }
        require(request.driver.licenseExpiry.isAfter(LocalDate.now())) { "Driver licence must be valid" }

        val now = Instant.now()
        val driver = drivers.save(
            RentalDriverEntity(
                vendorId = vendorId,
                fullName = request.driver.fullName.trim(),
                mobile = request.driver.mobile,
                licenseNumber = request.driver.licenseNumber.trim().uppercase(),
                licenseExpiry = request.driver.licenseExpiry,
                address = request.driver.address?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now
            )
        )
        val car = cars.save(
            RentalCarEntity(
                name = request.name.trim(),
                category = request.category.trim(),
                seats = request.seats,
                transmission = request.transmission.trim(),
                pricePerDay = request.pricePerDay.setScale(2, RoundingMode.HALF_UP),
                active = false,
                vendorId = vendorId,
                driverId = requireNotNull(driver.id),
                registrationNumber = request.registrationNumber.trim().uppercase(),
                make = request.make.trim(),
                model = request.model.trim(),
                variant = request.variant?.trim()?.takeIf { it.isNotBlank() },
                manufacturingYear = request.manufacturingYear,
                fuelType = request.fuelType.trim(),
                registrationYear = request.registrationYear,
                pickupAddress = request.pickupAddress.trim(),
                city = request.city.trim(),
                state = request.state.trim(),
                imageUrl = request.imageUrl?.trim()?.takeIf { it.isNotBlank() },
                approvalStatus = "PENDING_REVIEW"
            )
        )
        return toCarResponse(car)
    }

    fun availableCars(): List<RentalCarResponse> =
        cars.findAllByActiveTrueAndApprovalStatusAndVendorIdIsNotNullOrderByPricePerDayAsc("APPROVED")
            .map(::toCarResponse)

    fun bookings(userId: Long, page: Int, size: Int): RentalBookingPageResponse {
        require(page >= 0)
        require(size in 1..100)
        val result = bookings.findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
        val carMap = cars.findAllById(result.content.map { it.carId }).associateBy { it.id }
        return RentalBookingPageResponse(
            result.content.map { b ->
                val car = carMap[b.carId]
                val driver = car?.driverId?.let { drivers.findById(it).orElse(null) }
                toBookingResponse(b, car?.name ?: "Car", driver)
            },
            result.number, result.size, result.totalElements, result.totalPages, result.hasNext()
        )
    }

    @Transactional
    fun createBooking(userId: Long, request: RentalBookingRequest): RentalBookingResponse {
        val carId = request.carId.toLongOrNull() ?: throw IllegalArgumentException("Invalid car id")
        val car = cars.findByIdForUpdate(carId).orElseThrow { IllegalArgumentException("Rental car not found") }
        check(car.active && car.approvalStatus == "APPROVED" && car.vendorId != null && car.driverId != null) { "Rental car is not available" }
        require(request.paymentMethod.equals("WALLET", true)) { "This booking flow currently supports wallet payment" }
        require(request.pickupLocation.isNotBlank() && request.dropLocation.isNotBlank()) { "Pickup and drop locations are required" }
        require(request.endDate.isAfter(request.startDate)) { "End date must be after start date" }
        require(!request.startDate.isBefore(LocalDate.now())) { "Start date cannot be in the past" }
        check(!bookings.existsOverlapping(carId, listOf("PENDING", "CONFIRMED"), request.startDate, request.endDate)) { "This car is already booked for the selected dates" }

        val days = ChronoUnit.DAYS.between(request.startDate, request.endDate)
        val total = car.pricePerDay.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP)
        val bookingId = "RNT-" + UUID.randomUUID().toString().replace("-", "").take(20).uppercase()
        val ledgerRef = "RENTAL:$bookingId"
        wallet.reserve(userId, total)
        wallet.finalizeReservedDebit(userId, total, ledgerRef, bookingId)

        val now = Instant.now()
        val saved = bookings.save(
            RentalBookingEntity(
                bookingId = bookingId, userId = userId, carId = carId,
                pickupLocation = request.pickupLocation.trim(), dropLocation = request.dropLocation.trim(),
                startDate = request.startDate, endDate = request.endDate, totalAmount = total,
                status = "CONFIRMED", walletLedgerRef = ledgerRef, paymentMethod = "WALLET",
                createdAt = now, updatedAt = now
            )
        )
        return toBookingResponse(saved, car.name, drivers.findById(requireNotNull(car.driverId)).orElse(null))
    }

    @Transactional
    fun cancelBooking(userId: Long, bookingId: String): RentalBookingResponse {
        val booking = bookings.findByBookingIdAndUserId(bookingId, userId).orElseThrow { IllegalArgumentException("Rental booking not found") }
        check(booking.status == "CONFIRMED") { "Only confirmed bookings can be cancelled" }
        check(booking.startDate.isAfter(LocalDate.now())) { "Bookings starting today cannot be cancelled" }
        booking.status = "CANCELLED"
        booking.updatedAt = Instant.now()
        wallet.credit(userId, booking.totalAmount, "RENTAL_REFUND:$bookingId", "RENTAL_REFUND", bookingId, "Car rental refund")
        val car = cars.findById(booking.carId).orElse(null)
        bookings.save(booking)
        return toBookingResponse(booking, car?.name ?: "Car", car?.driverId?.let { drivers.findById(it).orElse(null) })
    }

    @Transactional
    fun approveVendor(vendorId: Long): RentalVendorResponse {
        val vendor = vendors.findById(vendorId).orElseThrow { IllegalArgumentException("Vendor not found") }
        vendor.status = "VERIFIED"
        vendor.rejectionReason = null
        vendor.updatedAt = Instant.now()
        vendors.save(vendor)
        return vendor(vendor.userId)
    }

    @Transactional
    fun rejectVendor(vendorId: Long, reason: String?): RentalVendorResponse {
        val vendor = vendors.findById(vendorId).orElseThrow { IllegalArgumentException("Vendor not found") }
        require(vendor.status != "VERIFIED") { "Verified vendors cannot be rejected from this action" }
        vendor.status = "REJECTED"
        vendor.rejectionReason = reason?.trim()?.takeIf { it.isNotBlank() } ?: "Additional information is required"
        vendor.updatedAt = Instant.now()
        vendors.save(vendor)
        return vendor(vendor.userId)
    }

    fun adminVendors(): List<RentalAdminVendorResponse> =
        vendors.findAllByOrderByCreatedAtDesc().map { v ->
            val user = users.findById(v.userId).orElse(null)
            RentalAdminVendorResponse(
                vendorId = requireNotNull(v.id).toString(),
                userId = v.userId.toString(),
                fullName = v.fullName,
                businessName = v.businessName,
                mobile = user?.mobile,
                email = user?.email,
                vendorType = v.vendorType,
                status = v.status,
                address = v.address,
                city = v.city,
                state = v.state,
                pinCode = v.pinCode,
                panNumber = v.panNumber,
                payoutUpiId = v.payoutUpiId,
                bankAccountNumber = v.bankAccountNumber,
                bankIfsc = v.bankIfsc,
                vehicleCount = cars.countByVendorId(requireNotNull(v.id)),
                rejectionReason = v.rejectionReason,
                submittedAt = v.createdAt,
                updatedAt = v.updatedAt
            )
        }

    fun adminVendorCars(vendorId: Long): List<RentalCarResponse> {
        vendors.findById(vendorId).orElseThrow { IllegalArgumentException("Vendor not found") }
        return cars.findAllByVendorIdOrderByIdDesc(vendorId).map(::toCarResponse)
    }

    @Transactional
    fun approveVehicle(carId: Long): RentalCarResponse {
        val car = cars.findById(carId).orElseThrow { IllegalArgumentException("Vehicle not found") }
        require(car.vendorId != null && car.driverId != null) { "Vehicle is not fully onboarded" }
        car.approvalStatus = "APPROVED"; car.active = true; cars.save(car)
        return toCarResponse(car)
    }

    private fun verifiedVendor(userId: Long): RentalVendorEntity {
        val vendor = vendors.findByUserId(userId).orElseThrow { IllegalArgumentException("Complete vendor onboarding first") }
        check(vendor.status == "VERIFIED") { "Vendor onboarding is pending approval" }
        return vendor
    }

    private fun toCarResponse(car: RentalCarEntity): RentalCarResponse {
        val driver = car.driverId?.let { drivers.findById(it).orElse(null) }
        return RentalCarResponse(
            id = requireNotNull(car.id).toString(), name = car.name, category = car.category,
            seats = car.seats, transmission = car.transmission, fuelType = car.fuelType,
            registrationYear = car.registrationYear, city = car.city, pickupAddress = car.pickupAddress,
            imageUrl = car.imageUrl, pricePerDay = car.pricePerDay.setScale(2),
            driverName = driver?.fullName ?: "Driver assigned", driverMobile = driver?.mobile
        )
    }

    private fun toBookingResponse(b: RentalBookingEntity, carName: String, driver: RentalDriverEntity?) =
        RentalBookingResponse(
            bookingId = b.bookingId, carName = carName, driverName = driver?.fullName ?: "Driver",
            driverMobile = driver?.mobile, pickup = b.pickupLocation, drop = b.dropLocation,
            startDate = b.startDate, endDate = b.endDate, total = b.totalAmount.setScale(2),
            paymentMethod = b.paymentMethod, status = b.status, createdAt = b.createdAt
        )
}
