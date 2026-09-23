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
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.web.multipart.MultipartFile

@Service
class RentalService(
    private val vendors: RentalVendorRepository,
    private val drivers: RentalDriverRepository,
    private val cars: RentalCarRepository,
    private val bookings: RentalBookingRepository,
    private val vehicleUnavailability: RentalVehicleUnavailabilityRepository,
    private val users: UserRepository,
    private val rentalPayments: RentalPaymentService,
    private val rentalPaymentRepository: RentalPaymentRepository,
    private val rentalPayouts: RentalPayoutService,
    private val rentalImageStorage: RentalImageStorage,
    private val vendorReviews: RentalVendorReviewRepository,
    private val carReviews: RentalCarReviewRepository
) {
    fun vendor(userId: Long): RentalVendorResponse {
        val vendor = vendors.findByUserId(userId).orElse(null)
        return vendor?.let {
            RentalVendorResponse(
                vendorId = it.id.toString(),
                status = it.status,
                vendorType = it.vendorType,
                fullName = it.fullName,
                businessName = it.businessName,
                city = it.city,
                state = it.state,
                vehicleCount = cars.countByVendorId(requireNotNull(it.id)),
                address = it.address,
                pinCode = it.pinCode,
                panNumber = maskSensitive(it.panNumber),
                payoutUpiId = maskUpi(it.payoutUpiId),
                bankAccountNumber = maskLastFour(it.bankAccountNumber),
                bankIfsc = maskLastFour(it.bankIfsc),
                bankName = it.bankName,
                payoutPrimaryMethod = it.payoutPrimaryMethod,
                rejectionReason = it.rejectionReason,
                submittedAt = it.createdAt
            )
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
            current.bankName = request.bankName?.trim()?.takeIf { it.isNotBlank() }
            current.payoutPrimaryMethod = normalizePrimaryPayoutMethod(request.payoutPrimaryMethod, request.payoutUpiId, request.bankAccountNumber, request.bankIfsc)
            current.status = "PENDING"
            current.rejectionReason = null
            val now = Instant.now()
            current.updatedAt = now
            vendors.save(current)
            vendorReviews.save(RentalVendorReviewEntity(vendorId = requireNotNull(current.id), action = "RESUBMITTED", actorUserId = userId, createdAt = now))
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
                bankName = request.bankName?.trim()?.takeIf { it.isNotBlank() },
                payoutPrimaryMethod = normalizePrimaryPayoutMethod(request.payoutPrimaryMethod, request.payoutUpiId, request.bankAccountNumber, request.bankIfsc),
                createdAt = now,
                updatedAt = now
            )
        )
        vendorReviews.save(RentalVendorReviewEntity(vendorId = requireNotNull(saved.id), action = "SUBMITTED", actorUserId = userId, createdAt = now))
        return vendor(userId)
    }

    @Transactional
    fun updateVendor(userId: Long, request: RentalVendorUpdateRequest): RentalVendorResponse {
        val vendor = vendors.findByUserId(userId).orElseThrow { IllegalArgumentException("Complete vendor onboarding first") }
        val type = request.vendorType.trim().uppercase()
        require(type in setOf("INDIVIDUAL", "BUSINESS")) { "Vendor type must be INDIVIDUAL or BUSINESS" }

        vendor.vendorType = type
        vendor.fullName = request.fullName.trim()
        vendor.businessName = request.businessName?.trim()?.takeIf { it.isNotBlank() }
        vendor.address = request.address.trim()
        vendor.city = request.city.trim()
        vendor.state = request.state.trim()
        vendor.pinCode = request.pinCode.trim()
        val panInput = request.panNumber?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val upiInput = request.payoutUpiId?.trim()?.takeIf { it.isNotBlank() }
        val accountInput = request.bankAccountNumber?.trim()?.takeIf { it.isNotBlank() }
        val ifscInput = request.bankIfsc?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

        if (panInput != null && !panInput.contains('*')) vendor.panNumber = panInput
        if (upiInput != null && !upiInput.contains('*')) vendor.payoutUpiId = upiInput
        if (accountInput != null && !accountInput.contains('*')) vendor.bankAccountNumber = accountInput
        if (ifscInput != null && !ifscInput.contains('*')) vendor.bankIfsc = ifscInput
        request.bankName?.trim()?.takeIf { it.isNotBlank() }?.let { vendor.bankName = it }
        vendor.payoutPrimaryMethod = normalizePrimaryPayoutMethod(
            request.payoutPrimaryMethod,
            vendor.payoutUpiId,
            vendor.bankAccountNumber,
            vendor.bankIfsc
        )
        vendor.updatedAt = Instant.now()
        vendors.save(vendor)
        return vendor(userId)
    }

    private fun normalizePrimaryPayoutMethod(
        requested: String?,
        upiId: String?,
        bankAccountNumber: String?,
        bankIfsc: String?
    ): String? {
        val primary = requested?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val hasUpi = !upiId.isNullOrBlank()
        val hasBank = !bankAccountNumber.isNullOrBlank() && !bankIfsc.isNullOrBlank()
        return when (primary) {
            "UPI" -> { require(hasUpi) { "Primary UPI is selected but UPI ID is missing" }; "UPI" }
            "BANK" -> { require(hasBank) { "Primary bank payout is selected but bank account and IFSC are incomplete" }; "BANK" }
            null -> when {
                hasUpi && !hasBank -> "UPI"
                hasBank && !hasUpi -> "BANK"
                else -> null
            }
            else -> throw IllegalArgumentException("Primary payout method must be BANK or UPI")
        }
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
        require(request.driver.licenseExpiry.isAfter(LocalDateTime.now())) { "Driver licence must be valid" }

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
        carReviews.save(RentalCarReviewEntity(carId = requireNotNull(car.id), action = "SUBMITTED", actorUserId = userId, createdAt = now))
        return toCarResponse(car)
    }

    @Transactional
    fun resubmitVehicle(userId: Long, carId: Long, request: RentalVehicleUpdateRequest): RentalCarResponse {
        val vendor = verifiedVendor(userId)
        val vendorId = requireNotNull(vendor.id)
        val car = cars.findById(carId).orElseThrow { IllegalArgumentException("Vehicle not found") }
        require(car.vendorId == vendorId) { "Vehicle does not belong to this vendor" }
        require(car.approvalStatus == "REJECTED") { "Only rejected vehicles can be corrected and resubmitted" }
        val driverId = requireNotNull(car.driverId) { "Vehicle driver is missing" }
        val driver = drivers.findById(driverId).orElseThrow { IllegalArgumentException("Vehicle driver not found") }

        require(request.seats in 1..20) { "Seats must be between 1 and 20" }
        require(request.pricePerDay > BigDecimal.ZERO) { "Price per day must be greater than zero" }
        require(!cars.existsByRegistrationNumberIgnoreCaseAndIdNot(request.registrationNumber.trim(), carId)) {
            "A vehicle with this registration number already exists"
        }
        require(request.driver.licenseExpiry.isAfter(LocalDateTime.now())) { "Driver licence must be valid" }

        val now = Instant.now()
        driver.fullName = request.driver.fullName.trim()
        driver.mobile = request.driver.mobile.trim()
        driver.licenseNumber = request.driver.licenseNumber.trim().uppercase()
        driver.licenseExpiry = request.driver.licenseExpiry
        driver.address = request.driver.address?.trim()?.takeIf { it.isNotBlank() }
        driver.rejectionReason = null
        driver.updatedAt = now
        drivers.save(driver)

        car.name = request.name.trim()
        car.category = request.category.trim()
        car.seats = request.seats
        car.transmission = request.transmission.trim()
        car.pricePerDay = request.pricePerDay.setScale(2, RoundingMode.HALF_UP)
        car.registrationNumber = request.registrationNumber.trim().uppercase()
        car.make = request.make.trim()
        car.model = request.model.trim()
        car.variant = request.variant?.trim()?.takeIf { it.isNotBlank() }
        car.manufacturingYear = request.manufacturingYear
        car.fuelType = request.fuelType.trim()
        car.registrationYear = request.registrationYear
        car.pickupAddress = request.pickupAddress.trim()
        car.city = request.city.trim()
        car.state = request.state.trim()
        car.imageUrl = request.imageUrl?.trim()?.takeIf { it.isNotBlank() }
        car.approvalStatus = "PENDING_REVIEW"
        car.rejectionReason = null
        car.active = false
        cars.save(car)
        carReviews.save(RentalCarReviewEntity(carId = carId, action = "RESUBMITTED", actorUserId = userId, createdAt = now))
        return toCarResponse(car)
    }

    fun availableCars(
        userId: Long,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null,
        location: String? = null
    ): List<RentalPublicCarResponse> {
        if ((startDate == null) != (endDate == null)) {
            throw IllegalArgumentException("Both rental start and end dates are required")
        }
        if (startDate != null && endDate != null) {
            require(endDate.isAfter(startDate)) { "End date must be after start date" }
            require(!startDate.isBefore(LocalDateTime.now())) { "Start date cannot be in the past" }
        }
        val normalizedLocation = location?.trim()?.takeIf { it.isNotBlank() }

        val available = cars.findAllByActiveTrueAndApprovalStatusAndVendorIdIsNotNullOrderByPricePerDayAsc("APPROVED")
        if (available.isEmpty()) return emptyList()
        val vendorIds = available.mapNotNull { it.vendorId }.distinct()
        val vendorById = vendors.findAllById(vendorIds).associateBy { requireNotNull(it.id) }
        val driverIds = available.mapNotNull { it.driverId }.distinct()
        val driverById = drivers.findAllById(driverIds).associateBy { requireNotNull(it.id) }
        val availabilityWindow = if (startDate != null && endDate != null) startDate to endDate else null
        val blockedCarIds = availabilityWindow?.let { (windowStart, windowEnd) ->
            val carIds = available.mapNotNull { it.id }
            val blockedByBookings = bookings.findOverlappingCarIds(
                carIds,
                listOf("PENDING", "CONFIRMED"),
                windowStart,
                windowEnd
            )
            val blockedByOffMarket = vehicleUnavailability.findOverlappingCarIds(
                carIds,
                windowStart.toLocalDate(),
                windowEnd.toLocalDate()
            )
            blockedByBookings + blockedByOffMarket
        } ?: emptySet()
        val now = LocalDateTime.now()
        return available
            .filter { car ->
                val vendorId = car.vendorId
                val isOwnVehicle = vendorId != null && vendorById[vendorId]?.userId == userId
                val driver = car.driverId?.let { driverById[it] }
                val rentalEnd = endDate ?: now
                val hasUsableDriver = driver?.active == true && driver.licenseExpiry.isAfter(rentalEnd)
                val isDateAvailable = requireNotNull(car.id) !in blockedCarIds
                val matchesLocation = normalizedLocation == null ||
                    car.city?.contains(normalizedLocation, ignoreCase = true) == true ||
                    car.pickupAddress?.contains(normalizedLocation, ignoreCase = true) == true
                !isOwnVehicle && hasUsableDriver && isDateAvailable && matchesLocation
            }
            .map(::toPublicCarResponse)
    }

    private fun requireNotOwnVehicle(userId: Long, car: RentalCarEntity) {
        val vendorId = requireNotNull(car.vendorId) { "Rental vehicle vendor is missing" }
        val vendor = vendors.findById(vendorId).orElseThrow { IllegalArgumentException("Rental vehicle vendor not found") }
        require(vendor.userId != userId) { "This vehicle cannot be booked by its owning vendor" }
    }

    private fun requireBookableDriver(car: RentalCarEntity, rentalEnd: LocalDateTime): RentalDriverEntity {
        val driverId = requireNotNull(car.driverId) { "Rental vehicle driver is missing" }
        val driver = drivers.findById(driverId).orElseThrow { IllegalArgumentException("Driver not found") }
        check(driver.active) { "Rental vehicle driver is unavailable" }
        check(driver.licenseExpiry.isAfter(rentalEnd)) { "Rental vehicle driver licence expires before the booking ends" }
        return driver
    }

    fun vendorEarnings(userId: Long): RentalVendorEarningsResponse {
        val vendor = verifiedVendor(userId)
        val vendorId = requireNotNull(vendor.id)
        val carIds = cars.findAllByVendorIdOrderByIdDesc(vendorId).mapNotNull { it.id }
        val now = Instant.now()
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val todayStart = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val tomorrowStart = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
        val monthStart = java.time.LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant()
        val nextMonthStart = java.time.LocalDate.now(zone).plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant()

        fun period(from: Instant, to: Instant): RentalVendorEarningsPeriodResponse {
            val paid = rentalPayouts.findByVendorUserId(userId).filter {
                it.status.equals("PAID", true) && !it.createdAt.isBefore(from) && it.createdAt.isBefore(to)
            }
            val gross = paid.fold(BigDecimal.ZERO) { acc, row -> acc + row.grossAmount }
            val fee = paid.fold(BigDecimal.ZERO) { acc, row -> acc + row.platformFeeAmount }
            val net = paid.fold(BigDecimal.ZERO) { acc, row -> acc + row.vendorNetAmount }
            val bookingCount = if (carIds.isEmpty()) {
                0L
            } else {
                bookings.countByCarIdInAndStatusInAndCreatedAtBetween(
                    carIds,
                    listOf("CONFIRMED", "COMPLETED"),
                    from,
                    to
                )
            }
            return RentalVendorEarningsPeriodResponse(
                grossAmount = gross.setScale(2, RoundingMode.HALF_UP),
                platformFeeAmount = fee.setScale(2, RoundingMode.HALF_UP),
                vendorNetAmount = net.setScale(2, RoundingMode.HALF_UP),
                bookingCount = bookingCount,
                completedBookingCount = paid.size.toLong()
            )
        }

        val upcomingBookingCount = if (carIds.isEmpty()) {
            0L
        } else {
            bookings.countByCarIdInAndStatusAndStartDateAfter(carIds, "CONFIRMED", LocalDateTime.now())
        }

        return RentalVendorEarningsResponse(
            today = period(todayStart, tomorrowStart),
            monthly = period(monthStart, nextMonthStart),
            upcomingBookingCount = upcomingBookingCount
        )
    }

    fun vendorPayouts(userId: Long): List<RentalVendorPayoutResponse> {
        verifiedVendor(userId)
        val payoutRows = rentalPayouts.findByVendorUserId(userId)
        if (payoutRows.isEmpty()) return emptyList()
        val bookingsById = bookings.findAllByBookingIdIn(payoutRows.map { it.bookingId }).associateBy { it.bookingId }
        val carIds = bookingsById.values.map { it.carId }.distinct()
        val carsById = cars.findAllById(carIds).associateBy { requireNotNull(it.id) }
        return payoutRows.map { row ->
            val booking = bookingsById[row.bookingId]
            val carId = requireNotNull(booking?.carId) { "Rental booking not found for payout" }
            val car = carsById[carId]
            RentalVendorPayoutResponse(
                payoutId = row.payoutId, bookingId = row.bookingId, carId = carId.toString(),
                carName = car?.name ?: "Car", grossAmount = row.grossAmount,
                platformFeePercent = row.platformFeePercent, platformFeeAmount = row.platformFeeAmount,
                vendorNetAmount = row.vendorNetAmount, status = row.status,
                createdAt = row.createdAt, paidAt = row.paidAt
            )
        }
    }

    fun bookings(userId: Long, page: Int, size: Int): RentalBookingPageResponse {
        require(page >= 0)
        require(size in 1..100)
        val result = bookings.findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
        val carMap = cars.findAllById(result.content.map { it.carId }).associateBy { it.id }
        return RentalBookingPageResponse(
            result.content.map { b ->
                val car = carMap[b.carId]
                val driver = car?.driverId?.let { drivers.findById(it).orElse(null) }
                toBookingResponse(b, car, driver)
            },
            result.number, result.size, result.totalElements, result.totalPages, result.hasNext()
        )
    }

    fun quoteBooking(userId: Long, request: RentalBookingQuoteRequest): RentalBookingQuoteResponse {
        val carId = request.carId.toLongOrNull() ?: throw IllegalArgumentException("Invalid car id")
        val car = cars.findById(carId).orElseThrow { IllegalArgumentException("Rental car not found") }
        check(car.active && car.approvalStatus == "APPROVED" && car.vendorId != null && car.driverId != null) { "Rental car is not available" }
        requireNotOwnVehicle(userId, car)
        require(request.pickupLocation.isNotBlank() && request.dropLocation.isNotBlank()) { "Pickup and drop locations are required" }
        require(request.endDate.isAfter(request.startDate)) { "End date must be after start date" }
        require(!request.startDate.isBefore(LocalDateTime.now())) { "Start date cannot be in the past" }
        check(!bookings.existsOverlapping(carId, listOf("PENDING", "CONFIRMED"), request.startDate, request.endDate)) { "This car is already booked for the selected dates" }
        check(!vehicleUnavailability.existsOverlapping(carId, request.startDate.toLocalDate(), request.endDate.toLocalDate())) { "This vehicle is unavailable for the selected dates" }
        val driver = requireBookableDriver(car, request.endDate)
        val durationMinutes = ChronoUnit.MINUTES.between(request.startDate, request.endDate)
        val days = ((durationMinutes + 1439) / 1440).coerceAtLeast(1)
        val total = car.pricePerDay.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP)
        return RentalBookingQuoteResponse(request.carId, car.name, driver.fullName, request.pickupLocation.trim(), request.dropLocation.trim(), request.startDate, request.endDate, days, car.pricePerDay.setScale(2), total)
    }

    @Transactional
    fun createBooking(userId: Long, request: RentalBookingRequest): RentalBookingResponse {
        val existingPayment = rentalPaymentRepository.findByUserIdAndClientRequestId(userId, request.clientRequestId.trim()).orElse(null)
        if (existingPayment != null) {
            val existingBooking = bookings.findByBookingIdAndUserId(existingPayment.bookingId, userId)
                .orElseThrow { IllegalStateException("Rental payment exists without its booking") }
            val existingCar = cars.findById(existingBooking.carId).orElse(null)
            return toBookingResponse(
                existingBooking,
                existingCar,
                existingCar?.driverId?.let { drivers.findById(it).orElse(null) }
            )
        }

        val carId = request.carId.toLongOrNull() ?: throw IllegalArgumentException("Invalid car id")
        val car = cars.findByIdForUpdate(carId).orElseThrow { IllegalArgumentException("Rental car not found") }
        check(car.active && car.approvalStatus == "APPROVED" && car.vendorId != null && car.driverId != null) { "Rental car is not available" }
        val racedExistingPayment = rentalPaymentRepository
            .findByUserIdAndClientRequestId(userId, request.clientRequestId.trim())
            .orElse(null)
        if (racedExistingPayment != null) {
            val existingBooking = bookings.findByBookingIdAndUserId(racedExistingPayment.bookingId, userId)
                .orElseThrow { IllegalStateException("Rental payment exists without its booking") }
            val existingCar = cars.findById(existingBooking.carId).orElse(null)
            return toBookingResponse(
                existingBooking,
                existingCar,
                existingCar?.driverId?.let { drivers.findById(it).orElse(null) }
            )
        }
        requireNotOwnVehicle(userId, car)
        require(request.paymentMethod.equals("WALLET", true)) { "This booking flow currently supports wallet payment" }
        require(request.pickupLocation.isNotBlank() && request.dropLocation.isNotBlank()) { "Pickup and drop locations are required" }
        require(request.endDate.isAfter(request.startDate)) { "End date must be after start date" }
        require(!request.startDate.isBefore(LocalDateTime.now())) { "Start date cannot be in the past" }
        val driver = requireBookableDriver(car, request.endDate)
        check(!bookings.existsOverlapping(carId, listOf("PENDING", "CONFIRMED"), request.startDate, request.endDate)) { "This car is already booked for the selected dates" }
        check(!vehicleUnavailability.existsOverlapping(carId, request.startDate.toLocalDate(), request.endDate.toLocalDate())) { "This vehicle is unavailable for the selected dates" }

        val durationMinutes = ChronoUnit.MINUTES.between(request.startDate, request.endDate)
        val days = ((durationMinutes + 1439) / 1440).coerceAtLeast(1)
        val total = car.pricePerDay.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP)
        val bookingId = "RNT-" + UUID.randomUUID().toString().replace("-", "").take(20).uppercase()
        val payment = rentalPayments.pay(
            userId = userId,
            bookingId = bookingId,
            amount = total,
            method = request.paymentMethod,
            clientRequestId = request.clientRequestId.trim()
        )

        val now = Instant.now()
        val saved = bookings.save(
            RentalBookingEntity(
                bookingId = bookingId, userId = userId, carId = carId,
                pickupLocation = request.pickupLocation.trim(), dropLocation = request.dropLocation.trim(),
                startDate = request.startDate, endDate = request.endDate, totalAmount = total,
                status = "CONFIRMED", walletLedgerRef = payment.walletLedgerRef,
                paymentMethod = payment.method, paymentId = payment.id,
                createdAt = now, updatedAt = now
            )
        )
        return toBookingResponse(saved, car, driver)
    }

    @Transactional
    fun uploadVehiclePhoto(
        userId: Long,
        carId: Long,
        slot: Int,
        photo: MultipartFile
    ): RentalCarResponse {
        val vendor = verifiedVendor(userId)
        val vendorId = requireNotNull(vendor.id)
        require(slot in 0..3) { "Vehicle photo slot must be between 0 and 3" }

        val car = cars.findByIdForUpdate(carId)
            .orElseThrow { IllegalArgumentException("Vehicle not found") }
        require(car.vendorId == vendorId) { "Vehicle does not belong to this vendor" }

        val slots = rentalPhotoSlots(car.imageUrl)
        val oldValue = slots[slot]
        val oldStoredKey = oldValue
            .removePrefix(RENTAL_PHOTO_URL_PREFIX)
            .takeIf { oldValue.startsWith(RENTAL_PHOTO_URL_PREFIX) }

        val newKey = rentalImageStorage.save(carId, slot, photo)
        slots[slot] = RENTAL_PHOTO_URL_PREFIX + newKey
        val combined = slots.joinToString("|").takeIf { slots.any { it.isNotBlank() } }
        require(combined == null || combined.length <= 500) {
            rentalImageStorage.delete(newKey)
            "Vehicle photo references exceed the maximum supported length"
        }

        try {
            car.imageUrl = combined
            cars.save(car)
        } catch (error: Exception) {
            rentalImageStorage.delete(newKey)
            throw error
        }

        if (oldStoredKey != null) {
            rentalImageStorage.delete(oldStoredKey)
        }
        return toCarResponse(car)
    }

    @Transactional
    fun uploadDriverPhoto(userId: Long, driverId: Long, photo: MultipartFile): RentalCarResponse {
        val vendor = verifiedVendor(userId)
        val vendorId = requireNotNull(vendor.id)
        val driver = drivers.findById(driverId).orElseThrow { IllegalArgumentException("Driver not found") }
        require(driver.vendorId == vendorId) { "Driver does not belong to this vendor" }

        val oldValue = driver.photoUrl.orEmpty()
        val oldStoredKey = oldValue.removePrefix(RENTAL_PHOTO_URL_PREFIX)
            .takeIf { oldValue.startsWith(RENTAL_PHOTO_URL_PREFIX) }
        val newKey = rentalImageStorage.saveDriverPhoto(driverId, photo)
        try {
            driver.photoUrl = RENTAL_PHOTO_URL_PREFIX + newKey
            driver.updatedAt = Instant.now()
            drivers.save(driver)
        } catch (error: Exception) {
            rentalImageStorage.delete(newKey)
            throw error
        }
        if (oldStoredKey != null) rentalImageStorage.delete(oldStoredKey)

        val car = cars.findAllByVendorIdOrderByIdDesc(vendorId).firstOrNull { it.driverId == driverId }
            ?: throw IllegalArgumentException("Vehicle for driver not found")
        return toCarResponse(car)
    }

    fun rentalImage(key: String): RentalImageStorage.StoredImage =
        rentalImageStorage.load(key) ?: throw IllegalArgumentException("Vehicle photo not found")

    @Transactional
    fun takeVehicleOffMarket(
        userId: Long,
        carId: Long,
        request: RentalVehicleUnavailabilityRequest
    ): RentalVehicleUnavailabilityResponse {
        val vendor = verifiedVendor(userId)
        val vendorId = requireNotNull(vendor.id)
        // Serialize vendor availability changes with customer booking creation.
        // Booking creation also acquires this same pessimistic car-row lock.
        val car = cars.findByIdForUpdate(carId).orElseThrow { IllegalArgumentException("Vehicle not found") }
        require(car.vendorId == vendorId) { "Vehicle does not belong to this vendor" }
        check(car.active && car.approvalStatus == "APPROVED") { "Only approved active vehicles can be taken off market" }
        require(!request.startDate.isBefore(LocalDate.now())) { "Off-market period cannot start in the past" }
        require(!request.endDate.isBefore(request.startDate)) { "End date must be on or after start date" }

        val reasonCode = request.reasonCode.trim().uppercase()
        require(reasonCode in rentalVehicleOffMarketReasons()) { "Invalid vehicle unavailability reason" }

        check(
            !bookings.existsOverlapping(
                carId,
                listOf("PENDING", "CONFIRMED"),
                request.startDate.atStartOfDay(),
                request.endDate.plusDays(1).atStartOfDay()
            )
        ) { "This vehicle already has a booking in the selected period" }
        check(!vehicleUnavailability.existsOverlapping(carId, request.startDate, request.endDate)) {
            "This vehicle is already marked unavailable for an overlapping period"
        }

        val now = Instant.now()
        val saved = vehicleUnavailability.save(
            RentalVehicleUnavailabilityEntity(
                carId = carId,
                vendorId = vendorId,
                vendorUserId = userId,
                startDate = request.startDate,
                endDate = request.endDate,
                reasonCode = reasonCode,
                reasonNote = request.reasonNote?.trim()?.takeIf { it.isNotBlank() },
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now
            )
        )
        return toVehicleUnavailabilityResponse(saved)
    }

    fun vendorVehicleUnavailability(userId: Long, carId: Long): List<RentalVehicleUnavailabilityResponse> {
        val vendor = verifiedVendor(userId)
        val vendorId = requireNotNull(vendor.id)
        val car = cars.findById(carId).orElseThrow { IllegalArgumentException("Vehicle not found") }
        require(car.vendorId == vendorId) { "Vehicle does not belong to this vendor" }
        return vehicleUnavailability.findAllByCarIdOrderByStartDateAsc(carId)
            .filter { it.status == "ACTIVE" && !it.endDate.isBefore(LocalDate.now()) }
            .map(::toVehicleUnavailabilityResponse)
    }

    @Transactional
    fun restoreVehicleToMarket(userId: Long, carId: Long, unavailableId: Long) {
        val vendor = verifiedVendor(userId)
        val vendorId = requireNotNull(vendor.id)
        val car = cars.findById(carId).orElseThrow { IllegalArgumentException("Vehicle not found") }
        require(car.vendorId == vendorId) { "Vehicle does not belong to this vendor" }
        val period = vehicleUnavailability.findById(unavailableId)
            .orElseThrow { IllegalArgumentException("Unavailable period not found") }
        require(period.carId == carId && period.vendorUserId == userId) { "Unavailable period does not belong to this vendor" }
        if (period.status != "ACTIVE") return
        period.status = "CANCELLED"
        period.updatedAt = Instant.now()
        vehicleUnavailability.save(period)
    }

    fun vehicleCalendar(
        userId: Long,
        carId: Long,
        year: Int,
        month: Int
    ): RentalVehicleCalendarResponse {
        require(month in 1..12) { "Month must be between 1 and 12" }
        val yearMonth = YearMonth.of(year, month)
        val vendor = verifiedVendor(userId)
        val vendorId = requireNotNull(vendor.id)
        val car = cars.findById(carId).orElseThrow { IllegalArgumentException("Vehicle not found") }
        require(car.vendorId == vendorId) { "Vehicle does not belong to this vendor" }

        val firstDay = yearMonth.atDay(1)
        val nextMonth = yearMonth.plusMonths(1).atDay(1)
        val bookingRows = bookings.findCalendarBookings(
            carId,
            listOf("PENDING", "CONFIRMED", "COMPLETED"),
            firstDay.atStartOfDay(),
            nextMonth.atStartOfDay()
        )
        val unavailableRows = vehicleUnavailability.findAllByCarIdOrderByStartDateAsc(carId)
            .filter { it.status == "ACTIVE" && !it.endDate.isBefore(firstDay) && !it.startDate.isAfter(yearMonth.atEndOfMonth()) }

        val days = (1..yearMonth.lengthOfMonth()).map { dayOfMonth ->
            val day = yearMonth.atDay(dayOfMonth)
            val dayStart = day.atStartOfDay()
            val dayEnd = day.plusDays(1).atStartOfDay()
            val booking = bookingRows.firstOrNull {
                it.startDate.isBefore(dayEnd) && it.endDate.isAfter(dayStart)
            }
            val blackout = unavailableRows.firstOrNull {
                !it.startDate.isAfter(day) && !it.endDate.isBefore(day)
            }
            when {
                booking != null -> RentalVehicleCalendarDayResponse(
                    date = day, status = "BOOKED", bookingId = booking.bookingId
                )
                blackout != null -> RentalVehicleCalendarDayResponse(
                    date = day,
                    status = "OFF_MARKET",
                    reasonCode = blackout.reasonCode,
                    reasonLabel = rentalVehicleOffMarketReasonLabel(blackout.reasonCode)
                )
                else -> RentalVehicleCalendarDayResponse(date = day, status = "AVAILABLE")
            }
        }

        return RentalVehicleCalendarResponse(
            carId = carId.toString(),
            carName = car.name,
            year = year,
            month = month,
            days = days
        )
    }

    fun adminVehicleUnavailability(): List<RentalAdminVehicleUnavailabilityResponse> =
        vehicleUnavailability.findAll()
            .filter { it.status == "ACTIVE" && !it.endDate.isBefore(LocalDate.now()) }
            .sortedBy { it.startDate }
            .map { row ->
                val car = cars.findById(row.carId).orElse(null)
                RentalAdminVehicleUnavailabilityResponse(
                    id = requireNotNull(row.id).toString(),
                    carId = row.carId.toString(),
                    carName = car?.name ?: "Car",
                    vendorId = row.vendorId.toString(),
                    startDate = row.startDate,
                    endDate = row.endDate,
                    reasonCode = row.reasonCode,
                    reasonLabel = rentalVehicleOffMarketReasonLabel(row.reasonCode),
                    reasonNote = row.reasonNote,
                    status = row.status,
                    createdAt = row.createdAt
                )
            }

    private fun rentalVehicleOffMarketReasons(): Set<String> = setOf(
        "SERVICE_MAINTENANCE",
        "PRIVATE_USE",
        "DRIVER_UNAVAILABLE",
        "LEGAL_DOCUMENTATION",
        "PERSONAL_REASON",
        "OTHER"
    )

    private fun rentalVehicleOffMarketReasonLabel(code: String): String = when (code) {
        "SERVICE_MAINTENANCE" -> "Service / maintenance"
        "PRIVATE_USE" -> "Private use"
        "DRIVER_UNAVAILABLE" -> "Driver unavailable"
        "LEGAL_DOCUMENTATION" -> "Documentation / compliance"
        "PERSONAL_REASON" -> "Personal reason"
        else -> "Other"
    }

    private fun toVehicleUnavailabilityResponse(row: RentalVehicleUnavailabilityEntity) =
        RentalVehicleUnavailabilityResponse(
            id = requireNotNull(row.id).toString(),
            carId = row.carId.toString(),
            startDate = row.startDate,
            endDate = row.endDate,
            reasonCode = row.reasonCode,
            reasonLabel = rentalVehicleOffMarketReasonLabel(row.reasonCode),
            reasonNote = row.reasonNote,
            status = row.status,
            createdAt = row.createdAt
        )

    @Transactional
    fun completeBooking(bookingId: String, actorUserId: Long): RentalBookingResponse {
        val booking = bookings.findByBookingIdForUpdate(bookingId).orElseThrow { IllegalArgumentException("Rental booking not found") }
        check(booking.status == "CONFIRMED") { "Only confirmed rental bookings can be completed" }
        check(!booking.endDate.isAfter(LocalDateTime.now())) { "Rental booking has not ended yet" }
        booking.status = "COMPLETED"
        booking.updatedAt = Instant.now()
        val saved = bookings.save(booking)
        rentalPayouts.settleCompletedBooking(saved)
        val car = cars.findById(saved.carId).orElse(null)
        return toBookingResponse(saved, car, car?.driverId?.let { drivers.findById(it).orElse(null) })
    }

    @Transactional
    fun cancelBooking(userId: Long, bookingId: String): RentalBookingResponse {
        val booking = bookings.findByBookingIdForUpdate(bookingId).orElseThrow { IllegalArgumentException("Rental booking not found") }
        require(booking.userId == userId) { "Rental booking not found" }
        check(booking.status == "CONFIRMED") { "Only confirmed bookings can be cancelled" }
        check(booking.startDate.isAfter(LocalDateTime.now())) { "Bookings starting today cannot be cancelled" }
        booking.status = "CANCELLED"
        booking.updatedAt = Instant.now()
        val payment = rentalPaymentRepository.findByBookingIdAndUserId(bookingId, userId)
            .orElseThrow { IllegalStateException("Rental payment not found for booking") }
        rentalPayments.refund(payment)
        val car = cars.findById(booking.carId).orElse(null)
        bookings.save(booking)
        return toBookingResponse(booking, car, car?.driverId?.let { drivers.findById(it).orElse(null) })
    }

    fun adminDashboard(): RentalAdminDashboardResponse =
        RentalAdminDashboardResponse(
            totalBookings = bookings.count(),
            confirmedBookings = bookings.countByStatus("CONFIRMED"),
            activeBookings = bookings.countActive(LocalDateTime.now()),
            completedBookings = bookings.countByStatus("COMPLETED"),
            cancelledBookings = bookings.countByStatus("CANCELLED"),
            totalBookingValue = bookings.sumTotalAmount().setScale(2, RoundingMode.HALF_UP),
            totalRefunded = rentalPaymentRepository.sumRefundedAmount().setScale(2, RoundingMode.HALF_UP),
            totalVendorPayouts = rentalPayouts.totalPaidVendorAmount(),
            totalPlatformFees = rentalPayouts.totalPlatformFeeAmount()
        )

    fun adminBookings(page: Int, size: Int, status: String?): RentalAdminBookingPageResponse {
        require(page >= 0)
        require(size in 1..100)
        val normalizedStatus = status?.trim()?.uppercase()?.takeIf { it.isNotBlank() && it != "ALL" }
        val result = if (normalizedStatus == null) {
            bookings.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
        } else {
            bookings.findAllByStatusOrderByCreatedAtDesc(normalizedStatus, PageRequest.of(page, size))
        }
        val bookingRows = result.content
        val carMap = cars.findAllById(bookingRows.map { it.carId }.distinct()).associateBy { requireNotNull(it.id) }
        val userMap = users.findAllById(bookingRows.map { it.userId }.distinct()).associateBy { requireNotNull(it.id) }
        val vendorIds = carMap.values.mapNotNull { it.vendorId }.distinct()
        val vendorMap = vendors.findAllById(vendorIds).associateBy { requireNotNull(it.id) }
        val paymentMap = rentalPaymentRepository.findAllByBookingIdIn(bookingRows.map { it.bookingId }).associateBy { it.bookingId }
        return RentalAdminBookingPageResponse(
            items = bookingRows.map { booking ->
                val car = carMap[booking.carId]
                val vendor = car?.vendorId?.let { vendorMap[it] }
                val user = userMap[booking.userId]
                val payment = paymentMap[booking.bookingId]
                RentalAdminBookingResponse(
                    bookingId = booking.bookingId,
                    userId = booking.userId.toString(),
                    userName = user?.name,
                    userMobile = user?.mobile,
                    carId = booking.carId.toString(),
                    carName = car?.name ?: "Car",
                    vendorName = vendor?.businessName?.takeIf { it.isNotBlank() } ?: vendor?.fullName,
                    pickup = booking.pickupLocation,
                    drop = booking.dropLocation,
                    startDate = booking.startDate,
                    endDate = booking.endDate,
                    total = booking.totalAmount.setScale(2),
                    paymentMethod = booking.paymentMethod,
                    paymentStatus = payment?.status ?: "UNKNOWN",
                    walletLedgerRef = booking.walletLedgerRef,
                    status = booking.status,
                    createdAt = booking.createdAt
                )
            },
            page = result.number,
            size = result.size,
            totalItems = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext()
        )
    }

    @Transactional
    fun approveVendor(vendorId: Long, actorUserId: Long): RentalVendorResponse {
        val vendor = vendors.findById(vendorId).orElseThrow { IllegalArgumentException("Vendor not found") }
        vendor.status = "VERIFIED"
        vendor.rejectionReason = null
        vendor.updatedAt = Instant.now()
        vendors.save(vendor)
        vendorReviews.save(RentalVendorReviewEntity(vendorId = vendorId, action = "APPROVED", actorUserId = actorUserId, createdAt = Instant.now()))
        return vendor(vendor.userId)
    }

    @Transactional
    fun rejectVendor(vendorId: Long, reason: String?, actorUserId: Long): RentalVendorResponse {
        val vendor = vendors.findById(vendorId).orElseThrow { IllegalArgumentException("Vendor not found") }
        require(vendor.status != "VERIFIED") { "Verified vendors cannot be rejected from this action" }
        vendor.status = "REJECTED"
        vendor.rejectionReason = reason?.trim()?.takeIf { it.isNotBlank() } ?: "Additional information is required"
        vendor.updatedAt = Instant.now()
        vendors.save(vendor)
        vendorReviews.save(RentalVendorReviewEntity(vendorId = vendorId, action = "REJECTED", reason = vendor.rejectionReason, actorUserId = actorUserId, createdAt = Instant.now()))
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
                bankName = v.bankName,
                payoutPrimaryMethod = v.payoutPrimaryMethod,
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
    fun approveVehicle(carId: Long, actorUserId: Long): RentalCarResponse {
        val car = cars.findById(carId).orElseThrow { IllegalArgumentException("Vehicle not found") }
        require(car.vendorId != null && car.driverId != null) { "Vehicle is not fully onboarded" }
        car.approvalStatus = "APPROVED"; car.rejectionReason = null; car.active = true; cars.save(car)
        carReviews.save(RentalCarReviewEntity(carId = carId, action = "APPROVED", actorUserId = actorUserId, createdAt = Instant.now()))
        return toCarResponse(car)
    }

    @Transactional
    fun rejectVehicle(carId: Long, reason: String?, actorUserId: Long): RentalCarResponse {
        val car = cars.findById(carId).orElseThrow { IllegalArgumentException("Vehicle not found") }
        require(car.approvalStatus != "APPROVED") { "Approved vehicles cannot be rejected from this action" }
        car.approvalStatus = "REJECTED"
        car.rejectionReason = reason?.trim()?.takeIf { it.isNotBlank() } ?: "Additional vehicle information is required"
        car.active = false
        cars.save(car)
        carReviews.save(RentalCarReviewEntity(carId = carId, action = "REJECTED", reason = car.rejectionReason, actorUserId = actorUserId, createdAt = Instant.now()))
        return toCarResponse(car)
    }

    private fun verifiedVendor(userId: Long): RentalVendorEntity {
        val vendor = vendors.findByUserId(userId).orElseThrow { IllegalArgumentException("Complete vendor onboarding first") }
        check(vendor.status == "VERIFIED") { "Vendor onboarding is pending approval" }
        return vendor
    }

    private fun rentalPhotoSlots(imageUrl: String?): MutableList<String> {
        val values = imageUrl.orEmpty()
            .replace("\n", "|")
            .split("|")
            .take(4)
            .map { it.trim() }
        return MutableList(4) { index -> values.getOrNull(index).orEmpty() }
    }

    private fun toPublicCarResponse(car: RentalCarEntity): RentalPublicCarResponse {
        val driver = car.driverId?.let { drivers.findById(it).orElse(null) }
        return RentalPublicCarResponse(
            id = requireNotNull(car.id).toString(),
            name = car.name,
            category = car.category,
            seats = car.seats,
            transmission = car.transmission,
            fuelType = car.fuelType,
            registrationYear = car.registrationYear,
            city = car.city,
            pickupAddress = car.pickupAddress,
            imageUrl = car.imageUrl,
            pricePerDay = car.pricePerDay.setScale(2),
            driverName = driver?.fullName ?: "Driver assigned",
            driverPhotoUrl = rentalPhotoDisplayUrl(driver?.photoUrl),
            driverRating = null,
            make = car.make,
            model = car.model,
            variant = car.variant,
            manufacturingYear = car.manufacturingYear,
            state = car.state
        )
    }

    private fun toCarResponse(car: RentalCarEntity): RentalCarResponse {
        val driver = car.driverId?.let { drivers.findById(it).orElse(null) }
        return RentalCarResponse(
            id = requireNotNull(car.id).toString(), name = car.name, category = car.category,
            seats = car.seats, transmission = car.transmission, fuelType = car.fuelType,
            registrationYear = car.registrationYear, city = car.city, pickupAddress = car.pickupAddress,
            imageUrl = car.imageUrl, pricePerDay = car.pricePerDay.setScale(2),
            driverId = driver?.id?.toString(),
            driverName = driver?.fullName ?: "Driver assigned",
            driverMobile = driver?.mobile,
            driverPhotoUrl = rentalPhotoDisplayUrl(driver?.photoUrl),
            approvalStatus = car.approvalStatus, rejectionReason = car.rejectionReason,
            make = car.make, model = car.model, variant = car.variant,
            manufacturingYear = car.manufacturingYear, registrationNumber = car.registrationNumber,
            state = car.state, driverLicenseNumber = driver?.licenseNumber,
            driverLicenseExpiry = driver?.licenseExpiry, driverAddress = driver?.address
        )
    }

    private fun rentalPhotoDisplayUrl(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            RENTAL_PHOTO_URL_PREFIX + trimmed.removePrefix(RENTAL_PHOTO_URL_PREFIX)
        }
    }

    private fun maskLastFour(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        val suffix = raw.takeLast(4)
        return "*".repeat((raw.length - suffix.length).coerceAtLeast(1)) + suffix
    }

    private fun maskSensitive(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        if (raw.length <= 4) return "*".repeat(raw.length)
        return "*".repeat(raw.length - 4) + raw.takeLast(4)
    }

    private fun maskUpi(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        val at = raw.indexOf('@')
        if (at <= 0) return maskSensitive(raw)
        return raw.first() + "*".repeat((at - 1).coerceAtLeast(3)) + raw.substring(at)
    }

    companion object {
        private const val RENTAL_PHOTO_URL_PREFIX = "/api/v1/car-rental/photos/"
    }

    private fun toBookingResponse(b: RentalBookingEntity, car: RentalCarEntity?, driver: RentalDriverEntity?) =
        RentalBookingResponse(
            bookingId = b.bookingId, carName = car?.name ?: "Car", driverName = driver?.fullName ?: "Driver",
            driverMobile = driver?.mobile, carImageUrl = car?.imageUrl?.takeIf { it.isNotBlank() },
            pickup = b.pickupLocation, drop = b.dropLocation,
            startDate = b.startDate, endDate = b.endDate, total = b.totalAmount.setScale(2),
            paymentMethod = b.paymentMethod, status = b.status, createdAt = b.createdAt
        )
}
