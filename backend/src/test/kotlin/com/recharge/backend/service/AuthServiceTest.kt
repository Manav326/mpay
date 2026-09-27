package com.recharge.backend.service

import com.recharge.backend.domain.UserEntity
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.repository.WalletRepository
import com.recharge.backend.security.JwtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

class AuthServiceTest {

    private val users = Mockito.mock(UserRepository::class.java)
    private val wallets = Mockito.mock(WalletRepository::class.java)
    private val passwordEncoder = Mockito.mock(PasswordEncoder::class.java)
    private val jwtService = Mockito.mock(JwtService::class.java)
    private val commissionRateService = Mockito.mock(CommissionRateService::class.java)
    private val roleAccessService = Mockito.mock(RoleAccessService::class.java)
    private val otpService = Mockito.mock(OtpService::class.java)

    private val service = AuthService(
        users = users,
        wallets = wallets,
        passwordEncoder = passwordEncoder,
        jwtService = jwtService,
        commissionRateService = commissionRateService,
        roleAccessService = roleAccessService,
        otpService = otpService
    )

    @Test
    fun inactiveDeletedAccountCannotLogin() {
        val deleted = UserEntity(
            id = 18L,
            mobile = "9237863848",
            name = "Deleted Account",
            role = "DELETED",
            active = false,
            passwordHash = "redacted-password-hash"
        )
        Mockito.doReturn(Optional.of(deleted)).`when`(users).findByMobile("9237863848")

        val ex = assertThrows(IllegalStateException::class.java) {
            service.login(com.recharge.backend.api.LoginRequest("9237863848", "anything"))
        }

        assertEquals("User account is inactive", ex.message)
        Mockito.verifyNoInteractions(passwordEncoder, jwtService, wallets, commissionRateService, roleAccessService, otpService)
    }
}
