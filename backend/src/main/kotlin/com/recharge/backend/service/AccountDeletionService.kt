package com.recharge.backend.service

import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.UserRepository
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class AccountDeletionBlockedException(message: String) : RuntimeException(message)

@Service
class AccountDeletionService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val entityManager: EntityManager,
    private val profileImageStorage: ProfileImageStorage,
    private val rentalImageStorage: RentalImageStorage
) {

    @Transactional
    fun deleteAccount(userId: Long, password: String, confirmation: String) {
        require(confirmation.trim().uppercase() == "DELETE") {
            "Type DELETE to confirm account deletion"
        }

        val user = users.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        if (user.deletedAt != null || !user.active) {
            return
        }
        require(user.role.equals("CLIENT", true)) {
            "This account cannot be deleted from the mPay client app"
        }
        require(passwordEncoder.matches(password, user.passwordHash)) {
            "Incorrect password"
        }

        val wallet = entityManager.createNativeQuery(
            "select balance, reserved_balance from wallets where user_id = :userId"
        )
            .setParameter("userId", userId)
            .resultList
            .firstOrNull()

        if (wallet != null) {
            val balance = (wallet as Array<*>)[0]?.toString()?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            val reserved = (wallet as Array<*>)[1]?.toString()?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            if (balance.signum() != 0 || reserved.signum() != 0) {
                throw AccountDeletionBlockedException(
                    "Please withdraw or settle your remaining wallet balance before deleting your account"
                )
            }
        }

        val pendingWithdrawals = scalarLong("""
            select count(*) from wallet_withdrawals
            where user_id = :userId and status in ('PENDING', 'PROCESSING')
        """, userId)
        val pendingRecharges = scalarLong("""
            select count(*) from recharge_transactions
            where user_id = :userId
              and status in ('RESERVED', 'PENDING', 'PROCESSING')
        """, userId)
        if (pendingRecharges > 0) {
            throw AccountDeletionBlockedException(
                "Please wait until pending recharge transactions are completed before deleting your account"
            )
        }

        if (pendingWithdrawals > 0) {
            throw AccountDeletionBlockedException(
                "Please wait until pending withdrawals are completed before deleting your account"
            )
        }

        val activeCustomerBookings = scalarLong("""
            select count(*) from rental_bookings
            where user_id = :userId
              and status in ('PENDING', 'CONFIRMED')
              and end_date > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata')
        """, userId)
        if (activeCustomerBookings > 0) {
            throw AccountDeletionBlockedException(
                "Please complete or cancel your active rental bookings before deleting your account"
            )
        }

        val pendingRentalPayments = scalarLong("""
            select count(*) from rental_payments
            where user_id = :userId and status = 'PENDING'
        """, userId)
        if (pendingRentalPayments > 0) {
            throw AccountDeletionBlockedException(
                "Please wait until pending rental payments are completed before deleting your account"
            )
        }

        val vendorId = scalarLongNullable(
            "select id from rental_vendors where user_id = :userId",
            userId
        )

        if (vendorId != null) {
            val activeVendorBookings = scalarLongByVendor(vendorId)
            if (activeVendorBookings > 0) {
                throw AccountDeletionBlockedException(
                    "Please complete or close your active rental bookings before deleting your vendor account"
                )
            }

            val pendingVendorPayouts = scalarLong("""
                select count(*) from rental_payouts
                where vendor_user_id = :userId and status = 'PENDING'
            """, userId)
            if (pendingVendorPayouts > 0) {
                throw AccountDeletionBlockedException(
                    "Please wait until pending rental payouts are completed before deleting your account"
                )
            }
        }

        val oldMobile = user.mobile
        val profileImageKey = user.profileImageKey
        val rentalPhotoKeys = if (vendorId != null) collectVendorPhotoKeys(vendorId) else emptyList()

        entityManager.createNativeQuery(
            "delete from password_reset_otps where mobile = :mobile"
        ).setParameter("mobile", oldMobile).executeUpdate()

        // Retain financial records for reconciliation, but remove direct personal identifiers.
        entityManager.createNativeQuery("""
            update wallet_withdrawals
            set upi_id = 'deleted@redacted',
                updated_at = CURRENT_TIMESTAMP
            where user_id = :userId
        """).setParameter("userId", userId).executeUpdate()

        entityManager.createNativeQuery("""
            update recharge_transactions
            set mobile_number = '0000000000',
                recipient_name = null,
                operator = 'REDACTED',
                circle = 'REDACTED',
                plan_id = 'REDACTED',
                plan_description = null,
                plan_validity = null,
                updated_at = CURRENT_TIMESTAMP
            where user_id = :userId
        """).setParameter("userId", userId).executeUpdate()

        entityManager.createNativeQuery("""
            update payment_orders
            set recharge_mobile_number = null,
                recharge_operator = null,
                recharge_circle = null,
                recharge_plan_id = null,
                recharge_recipient_name = null
            where user_id = :userId
        """).setParameter("userId", userId).executeUpdate()

        entityManager.createNativeQuery("""
            update rental_bookings
            set pickup_location = 'REDACTED',
                drop_location = 'REDACTED',
                pickup_latitude = null,
                pickup_longitude = null,
                pickup_place_id = null,
                drop_latitude = null,
                drop_longitude = null,
                drop_place_id = null,
                updated_at = CURRENT_TIMESTAMP
            where user_id = :userId
        """).setParameter("userId", userId).executeUpdate()

        if (vendorId != null) {
            entityManager.createNativeQuery("""
                update rental_bookings b
                set pickup_location = 'REDACTED',
                    drop_location = 'REDACTED',
                    pickup_latitude = null,
                    pickup_longitude = null,
                    pickup_place_id = null,
                    drop_latitude = null,
                    drop_longitude = null,
                    drop_place_id = null,
                    updated_at = CURRENT_TIMESTAMP
                from rental_cars c
                where b.car_id = c.id and c.vendor_id = :vendorId
            """).setParameter("vendorId", vendorId).executeUpdate()

            entityManager.createNativeQuery("""
                update rental_vehicle_unavailability
                set reason_note = null,
                    reason_code = 'ACCOUNT_DELETED',
                    status = 'CANCELLED',
                    updated_at = CURRENT_TIMESTAMP
                where vendor_user_id = :userId
            """).setParameter("userId", userId).executeUpdate()

            entityManager.createNativeQuery("""
                update rental_vendor_review_history
                set reason = null
                where vendor_id = :vendorId or actor_user_id = :userId
            """)
                .setParameter("vendorId", vendorId)
                .setParameter("userId", userId)
                .executeUpdate()

            entityManager.createNativeQuery("""
                update rental_car_review_history h
                set reason = null
                where h.actor_user_id = :userId
                   or h.car_id in (select id from rental_cars where vendor_id = :vendorId)
            """)
                .setParameter("vendorId", vendorId)
                .setParameter("userId", userId)
                .executeUpdate()

            entityManager.createNativeQuery("""
                update rental_drivers
                set full_name = 'Deleted Driver',
                    mobile = '0000000000',
                    license_number = 'DELETED-' || id,
                    address = null,
                    photo_url = null,
                    active = false,
                    rejection_reason = null,
                    updated_at = CURRENT_TIMESTAMP
                where vendor_id = :vendorId
            """).setParameter("vendorId", vendorId).executeUpdate()

            entityManager.createNativeQuery("""
                update rental_cars
                set name = 'Deleted Vehicle',
                    category = 'REDACTED',
                    seats = 0,
                    transmission = 'REDACTED',
                    price_per_day = 0,
                    active = false,
                    registration_number = null,
                    make = null,
                    model = null,
                    variant = null,
                    manufacturing_year = null,
                    fuel_type = null,
                    registration_year = null,
                    pickup_address = null,
                    pickup_latitude = null,
                    pickup_longitude = null,
                    pickup_place_id = null,
                    city = null,
                    state = null,
                    image_url = null,
                    approval_status = 'DELETED',
                    rejection_reason = null
                where vendor_id = :vendorId
            """).setParameter("vendorId", vendorId).executeUpdate()

            entityManager.createNativeQuery("""
                update rental_vendors
                set vendor_type = 'DELETED',
                    status = 'DELETED',
                    full_name = 'Deleted Account',
                    business_name = null,
                    address = 'REDACTED',
                    city = 'REDACTED',
                    state = 'REDACTED',
                    pin_code = '000000',
                    pan_number = null,
                    payout_upi_id = null,
                    bank_account_number = null,
                    bank_ifsc = null,
                    bank_name = null,
                    payout_primary_method = null,
                    rejection_reason = null,
                    updated_at = CURRENT_TIMESTAMP
                where id = :vendorId
            """).setParameter("vendorId", vendorId).executeUpdate()
        }

        val redactedPassword = passwordEncoder.encode(UUID.randomUUID().toString())
        val replacementMobile = uniqueDeletedMobile()
        val replacementPublicId = UUID.randomUUID().toString()

        user.mobile = replacementMobile
        user.publicId = replacementPublicId
        user.name = "Deleted Account"
        user.email = null
        user.profileImageKey = null
        user.profileImageContentType = null
        user.profileImageUpdatedAt = null
        user.profileUpdatedAt = Instant.now()
        user.passwordHash = redactedPassword
        user.role = "DELETED"
        user.active = false
        user.deletedAt = Instant.now()
        users.save(user)

        if (!profileImageKey.isNullOrBlank()) {
            profileImageStorage.delete(profileImageKey)
        }
        rentalPhotoKeys.forEach(rentalImageStorage::delete)
    }

    private fun collectVendorPhotoKeys(vendorId: Long): List<String> {
        val rawRows = entityManager.createNativeQuery("""
            select image_url, driver_id
            from rental_cars
            where vendor_id = :vendorId
        """).setParameter("vendorId", vendorId).resultList

        val keys = mutableListOf<String>()
        for (row in rawRows) {
            val values = row as Array<*>
            keys += parseRentalPhotoKeys(values[0]?.toString())
        }

        val driverRows = entityManager.createNativeQuery("""
            select photo_url from rental_drivers where vendor_id = :vendorId
        """).setParameter("vendorId", vendorId).resultList
        driverRows.forEach { value ->
            val key = rentalPhotoKey(value?.toString())
            if (key != null) keys += key
        }
        return keys.distinct()
    }

    private fun parseRentalPhotoKeys(value: String?): List<String> =
        value.orEmpty()
            .replace("
", "|")
            .split("|")
            .mapNotNull(::rentalPhotoKey)
            .take(4)

    private fun rentalPhotoKey(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        val prefix = "/api/v1/car-rental/photos/"
        return trimmed.removePrefix(prefix)
            .takeIf { trimmed.startsWith(prefix) && it.isNotBlank() }
    }

    private fun scalarLong(sql: String, userId: Long): Long =
        (entityManager.createNativeQuery(sql)
            .setParameter("userId", userId)
            .singleResult as Number).toLong()

    private fun scalarLongNullable(sql: String, userId: Long): Long? =
        entityManager.createNativeQuery(sql)
            .setParameter("userId", userId)
            .resultList
            .firstOrNull()
            ?.let { (it as Number).toLong() }

    private fun scalarLongByVendor(vendorId: Long): Long =
        (entityManager.createNativeQuery("""
            select count(*) from rental_bookings b
            where b.car_id in (select id from rental_cars where vendor_id = :vendorId)
              and b.status in ('PENDING', 'CONFIRMED')
              and b.end_date > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata')
        """).setParameter("vendorId", vendorId).singleResult as Number).toLong()

    private fun uniqueDeletedMobile(): String {
        while (true) {
            val candidate = "9" + ThreadLocalRandom.current().nextLong(100_000_000L, 1_000_000_000L).toString()
            if (users.findByMobile(candidate).isEmpty) return candidate
        }
    }
}
