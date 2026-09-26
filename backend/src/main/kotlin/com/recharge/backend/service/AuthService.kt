package com.recharge.backend.service

import com.recharge.backend.api.CurrentUserResponse
import com.recharge.backend.api.LoginRequest
import com.recharge.backend.api.PortalLoginRequest
import com.recharge.backend.api.LoginResponse
import com.recharge.backend.api.RefreshTokenRequest
import com.recharge.backend.api.RegisterRequest
import com.recharge.backend.domain.UserEntity
import com.recharge.backend.domain.WalletEntity
import com.recharge.backend.repository.UserRepository
import com.recharge.backend.repository.WalletRepository
import com.recharge.backend.security.JwtService
import jakarta.transaction.Transactional
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val users: UserRepository,
    private val wallets: WalletRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val commissionRateService: CommissionRateService,
    private val roleAccessService: RoleAccessService,
    private val otpService: OtpService
) {

    @Transactional
    fun register(request: RegisterRequest): LoginResponse {
        val mobile = normalizeMobile(request.mobile)
        val email = request.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        if (users.findByMobile(mobile).isPresent) {
            throw IllegalArgumentException("A user with this mobile number already exists")
        }
        if (email != null && users.findByEmailIgnoreCase(email).isPresent) {
            throw IllegalArgumentException("A user with this email address already exists")
        }

        request.mobileVerificationToken?.trim()?.takeIf { it.isNotBlank() }?.let { token ->
            otpService.consumeRegistrationVerification(mobile, token)
        }

        val user = users.save(
            UserEntity(
                mobile = mobile,
                name = request.name?.trim()?.takeIf { it.isNotBlank() },
                email = email,
                passwordHash = passwordEncoder.encode(request.password),
                role = "CLIENT",
                active = true,
                mobileVerifiedAt = request.mobileVerificationToken?.trim()?.takeIf { it.isNotBlank() }?.let { java.time.Instant.now() }
            )
        )

        wallets.save(WalletEntity(user = user))
        return tokenResponse(user)
    }

    fun portalLogin(request: PortalLoginRequest): LoginResponse {
        val requestedRole = request.portalRole.trim().uppercase()
        require(roleAccessService.hasPermission(requestedRole, "PORTAL_LOGIN")) { "Unsupported portal role" }
        return login(LoginRequest(request.mobile, request.password), requestedRole, clientOnly = false)
    }

    fun adminLogin(request: LoginRequest): LoginResponse = login(request, "ADMIN", clientOnly = false)

    fun managerLogin(request: LoginRequest): LoginResponse = login(request, "MANAGER", clientOnly = false)

    fun login(request: LoginRequest): LoginResponse {
        return login(request, null, clientOnly = true)
    }

    private fun login(request: LoginRequest, requiredRole: String?, clientOnly: Boolean): LoginResponse {
        if (requiredRole != null && !roleAccessService.hasPermission(requiredRole, "PORTAL_LOGIN")) {
            throw BadCredentialsException("This role is not enabled for the mPay Admin Portal")
        }
        val mobile = normalizeMobile(request.mobile)
        val user = users.findByMobile(mobile).orElseThrow {
            BadCredentialsException("Invalid mobile number or password")
        }

        if (!user.active) throw IllegalStateException("User account is inactive")
        if (clientOnly && !user.role.equals("CLIENT", true)) {
            throw BadCredentialsException("This account must use the mPay Admin Portal")
        }
        if (requiredRole != null && !user.role.equals(requiredRole, true)) {
            throw BadCredentialsException("Invalid portal role for this account")
        }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw BadCredentialsException("Invalid mobile number or password")
        }

        return tokenResponse(user)
    }

    fun refresh(request: RefreshTokenRequest): LoginResponse {
        val claims = jwtService.parseAndValidate(request.refreshToken)
        if (!jwtService.isRefreshToken(claims)) throw IllegalArgumentException("Invalid refresh token")

        val userId = claims.subject.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid refresh token subject")
        val user = users.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        if (!user.active) throw IllegalStateException("User account is inactive")
        return tokenResponse(user)
    }

    fun currentUser(userId: Long): CurrentUserResponse {
        val user = users.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        return toCurrentUserResponse(user)
    }

    private fun toCurrentUserResponse(user: UserEntity): CurrentUserResponse {
        val userId = requireNotNull(user.id)
        val version = user.profileImageUpdatedAt?.toEpochMilli()
        val imageUrl = user.profileImageKey?.let { "/api/v1/profile/image" + (version?.let { v -> "?v=$v" } ?: "") }
        return CurrentUserResponse(
            userId = userId,
            publicUserId = user.publicId,
            mobile = user.mobile,
            name = user.name,
            email = user.email,
            profileImageUrl = imageUrl,
            profileImageVersion = version,
            mobileVerified = user.mobileVerifiedAt != null,
            role = user.role,
            commissionRate = commissionRateService.rateForRole(user.role),
            createdAt = user.createdAt,
            profileUpdatedAt = user.profileUpdatedAt
        )
    }

    private fun tokenResponse(user: UserEntity): LoginResponse {
        val userId = requireNotNull(user.id) { "User ID was not generated" }
        return LoginResponse(
            accessToken = jwtService.createAccessToken(userId, user.mobile, user.role),
            refreshToken = jwtService.createRefreshToken(userId, user.mobile, user.role),
            userId = userId,
            role = user.role,
            permissions = roleAccessService.permissionsFor(user.role),
            mobileVerified = user.mobileVerifiedAt != null
        )
    }

    private fun normalizeMobile(mobile: String): String = mobile.trim()
}
