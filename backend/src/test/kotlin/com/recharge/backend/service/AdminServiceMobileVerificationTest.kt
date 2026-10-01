package com.recharge.backend.service

import com.recharge.backend.domain.EmployeeEntity
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import java.time.Instant
import java.util.Optional

class AdminServiceMobileVerificationTest {
    private val users = Mockito.mock(UserRepository::class.java)
    private val employees = Mockito.mock(EmployeeRepository::class.java)
    private val wallets = Mockito.mock(WalletRepository::class.java)
    private val walletLedger = Mockito.mock(WalletTransactionRepository::class.java)
    private val recharges = Mockito.mock(RechargeTransactionRepository::class.java)
    private val withdrawals = Mockito.mock(WalletWithdrawalRepository::class.java)
    private val commissionRates = Mockito.mock(CommissionRateService::class.java)
    private val roleAccess = Mockito.mock(RoleAccessService::class.java)
    private val passwordEncoder = Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder::class.java)
    private val imageStorage = Mockito.mock(ProfileImageStorage::class.java)
    private val voiceCalls = Mockito.mock(VoiceCallService::class.java)
    private val employeeAudit = Mockito.mock(EmployeeAuditService::class.java)

    private val service = AdminService(
        users,
        employees,
        wallets,
        walletLedger,
        recharges,
        withdrawals,
        commissionRates,
        roleAccess,
        passwordEncoder,
        imageStorage,
        voiceCalls,
        employeeAudit
    )

    @Test
    fun adminCanMarkClientMobileVerifiedAndAuditReason() {
        val viewer = EmployeeEntity(id = 1L, role = "ADMIN", mobile = "9999999999")
        val client = UserEntity(id = 10L, publicId = "CLIENT-10", role = "CLIENT", mobile = "9000000010")

        Mockito.doReturn(Optional.of(client)).`when`(users).findByPublicId("CLIENT-10")

        val response = service.updateUserMobileVerification(
            viewer,
            "CLIENT-10",
            true,
            "Customer provided evidence that the recycled number is now assigned to them."
        )

        assertEquals(true, response.mobileVerified)
        assertNotNull(response.mobileVerifiedAt)
        assertNotNull(client.mobileVerifiedAt)

        Mockito.verify(roleAccess).requirePermission(viewer, "MANAGE_USER_MOBILE_VERIFICATION")
        Mockito.verify(users).save(client)
        Mockito.verify(employeeAudit).record(
            Mockito.eq(viewer),
            Mockito.eq("USER_MOBILE_VERIFICATION_CHANGED"),
            Mockito.eq("CLIENT"),
            Mockito.eq("CLIENT-10"),
            Mockito.eq("Marked client mobile number as verified."),
            Mockito.anyMap()
        )
    }

    @Test
    fun adminCanMarkClientMobileNotVerifiedWithoutDeletingAccountData() {
        val viewer = EmployeeEntity(id = 1L, role = "ADMIN", mobile = "9999999999")
        val verifiedAt = Instant.parse("2026-09-01T10:00:00Z")
        val client = UserEntity(
            id = 11L,
            publicId = "CLIENT-11",
            role = "CLIENT",
            mobile = "9000000011",
            mobileVerifiedAt = verifiedAt
        )

        Mockito.doReturn(Optional.of(client)).`when`(users).findByPublicId("CLIENT-11")

        val response = service.updateUserMobileVerification(
            viewer,
            "CLIENT-11",
            false,
            "The mobile number has been reassigned by the telecom operator."
        )

        assertEquals(false, response.mobileVerified)
        assertNull(response.mobileVerifiedAt)
        assertNull(client.mobileVerifiedAt)
        assertEquals("9000000011", client.mobile)
        assertEquals("CLIENT", client.role)
        Mockito.verify(users).save(client)
        Mockito.verify(employeeAudit).record(
            Mockito.eq(viewer),
            Mockito.eq("USER_MOBILE_VERIFICATION_CHANGED"),
            Mockito.eq("CLIENT"),
            Mockito.eq("CLIENT-11"),
            Mockito.eq("Marked client mobile number as not verified."),
            Mockito.anyMap()
        )
    }

    @Test
    fun verificationChangeRequiresReason() {
        val viewer = EmployeeEntity(id = 1L, role = "ADMIN", mobile = "9999999999")
        val client = UserEntity(id = 12L, publicId = "CLIENT-12", role = "CLIENT", mobile = "9000000012")

        Mockito.doReturn(Optional.of(client)).`when`(users).findByPublicId("CLIENT-12")

        assertThrows<IllegalArgumentException> {
            service.updateUserMobileVerification(viewer, "CLIENT-12", true, "  ")
        }

        assertNull(client.mobileVerifiedAt)
        Mockito.verify(users, Mockito.never()).save(client)
        Mockito.verify(employeeAudit, Mockito.never()).record(
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyMap()
        )
    }

    @Test
    fun verificationChangeIsClientOnly() {
        val viewer = EmployeeEntity(id = 1L, role = "ADMIN", mobile = "9999999999")
        val employeeAccount = UserEntity(id = 13L, publicId = "MANAGER-13", role = "MANAGER", mobile = "9000000013")

        Mockito.doReturn(Optional.of(employeeAccount)).`when`(users).findByPublicId("MANAGER-13")

        val error = assertThrows<IllegalArgumentException> {
            service.updateUserMobileVerification(
                viewer,
                "MANAGER-13",
                true,
                "Administrative correction."
            )
        }

        assertEquals("Mobile verification control is only available for client accounts", error.message)
        assertNull(employeeAccount.mobileVerifiedAt)
        Mockito.verify(users, Mockito.never()).save(employeeAccount)
    }
}
