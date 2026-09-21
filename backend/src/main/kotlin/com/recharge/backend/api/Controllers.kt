package com.recharge.backend.api

import com.recharge.backend.service.AuthService
import com.recharge.backend.service.RechargeHistoryService
import com.recharge.backend.service.RechargeService
import com.recharge.backend.service.WalletService
import com.recharge.backend.service.ProfileService
import com.recharge.backend.repository.RechargeTransactionRepository
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import com.recharge.backend.service.PaymentGatewayService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.security.access.AccessDeniedException
import java.time.LocalDate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class ClientController(
    private val wallet: WalletService,
    private val recharge: RechargeService,
    private val rechargeHistory: RechargeHistoryService,
    private val authService: AuthService,
    private val paymentGatewayService: PaymentGatewayService,
    private val rechargeRepository: RechargeTransactionRepository
) {
    private fun authenticatedUserId(authentication: Authentication): Long =
        authentication.name.toLongOrNull() ?: throw IllegalStateException("Invalid authenticated user")

    @GetMapping("/me")
    fun me(authentication: Authentication): CurrentUserResponse =
        authService.currentUser(authenticatedUserId(authentication))

    @GetMapping("/wallet")
    fun wallet(authentication: Authentication): WalletResponse {
        val snapshot = wallet.getWalletSnapshot(authenticatedUserId(authentication))
        return WalletResponse(
            balance = snapshot.balance,
            availableBalance = snapshot.availableBalance,
            reservedBalance = snapshot.reservedBalance
        )
    }

    @PostMapping("/payments/orders")
    fun createPaymentOrder(
        authentication: Authentication,
        @Valid @RequestBody request: CreatePaymentOrderRequest
    ): CreatePaymentOrderResponse =
        paymentGatewayService.createWalletOrder(authenticatedUserId(authentication), request)

    @PostMapping("/payments/verify")
    fun verifyPayment(
        authentication: Authentication,
        @Valid @RequestBody request: VerifyPaymentRequest
    ): VerifyPaymentResponse =
        paymentGatewayService.verifyWalletPayment(authenticatedUserId(authentication), request)

    @PostMapping("/recharge/operator")
    fun operator(@Valid @RequestBody request: OperatorCheckRequest) = recharge.detect(request)

    @GetMapping("/recharge/plans")
    fun plans(
        @RequestParam mobile: String,
        @RequestParam operator: String,
        @RequestParam circle: String,
        @RequestParam(required = false) providerOperator: String?,
        @RequestParam(required = false) providerCircle: String?
    ) = recharge.plans(mobile, operator, circle, providerOperator, providerCircle)

    @PostMapping("/recharge")
    fun recharge(
        authentication: Authentication,
        @Valid @RequestBody request: RechargeRequest
    ): RechargeResponse =
        recharge.recharge(authenticatedUserId(authentication), request)

    @GetMapping("/recharge/{transactionId}")
    fun rechargeStatus(
        authentication: Authentication,
        @PathVariable transactionId: String
    ): RechargeTransactionStatusResponse =
        recharge.transaction(authenticatedUserId(authentication), transactionId)

    @GetMapping("/recharge/history")
    fun rechargeHistory(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?
    ): RechargeHistoryResponse = rechargeHistory.history(
        authenticatedUserId(authentication), page, size,
        from?.let(java.time.LocalDate::parse), to?.let(java.time.LocalDate::parse)
    )

    @GetMapping("/recharge/commission-summary")
    fun rechargeCommissionSummary(authentication: Authentication): RechargeCommissionSummaryResponse =
        rechargeHistory.commissionSummary(authenticatedUserId(authentication))

    @GetMapping("/wallet/history")
    fun walletHistory(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?
    ): WalletHistoryResponse {
        val snapshot = wallet.walletHistory(authenticatedUserId(authentication), page, size, kind, from?.let(LocalDate::parse), to?.let(LocalDate::parse))
        return WalletHistoryResponse(
            items = snapshot.items.map { tx ->
                val recharge = if (tx.referenceType.equals("RECHARGE", true) && !tx.referenceId.isNullOrBlank()) {
                    rechargeRepository.findByTransactionId(tx.referenceId!!).orElse(null)
                } else null
                WalletHistoryItem(
                    id = tx.id ?: 0L, type = tx.type, amount = tx.amount, status = tx.status,
                    referenceType = tx.referenceType, referenceId = tx.referenceId, externalRef = tx.externalRef,
                    description = tx.description, createdAt = tx.createdAt,
                    mobileNumber = recharge?.mobileNumber, operator = recharge?.operator, circle = recharge?.circle
                )
            },
            page = snapshot.page, size = snapshot.size, totalItems = snapshot.totalItems, totalPages = snapshot.totalPages, hasNext = snapshot.hasNext,
            fromDate = snapshot.fromDate, toDate = snapshot.toDate
        )
    }

    @PostMapping("/wallet/withdraw")
    fun withdraw(authentication: Authentication, @Valid @RequestBody request: WithdrawMoneyRequest): WithdrawMoneyResponse {
        val snapshot = wallet.withdrawToUpi(authenticatedUserId(authentication), request.amount, request.upiId)
        return WithdrawMoneyResponse("SUCCESS", request.amount.setScale(2), request.upiId.trim(), snapshot.balance, snapshot.availableBalance)
    }
}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val passwordResetService: com.recharge.backend.service.PasswordResetService,
    private val roleAccessService: com.recharge.backend.service.RoleAccessService
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): LoginResponse = authService.register(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse = authService.login(request)

    @PostMapping("/portal-login")
    fun portalLogin(@Valid @RequestBody request: PortalLoginRequest): LoginResponse = authService.portalLogin(request)

    @PostMapping("/admin-login")
    fun adminLogin(@Valid @RequestBody request: LoginRequest): LoginResponse = authService.adminLogin(request)

    @PostMapping("/manager-login")
    fun managerLogin(@Valid @RequestBody request: LoginRequest): LoginResponse = authService.managerLogin(request)

    @GetMapping("/portal-roles")
    fun portalRoles(): PortalRolesResponse = PortalRolesResponse(roleAccessService.portalRoles())

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): LoginResponse = authService.refresh(request)

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ForgotPasswordResponse =
        passwordResetService.requestOtp(request.mobile)

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Map<String, String>> {
        passwordResetService.resetPassword(request)
        return ResponseEntity.ok(mapOf("status" to "PASSWORD_RESET_SUCCESS"))
    }
}


@RestController
@RequestMapping("/api/v1/profile")
class ProfileController(private val profileService: ProfileService) {
    private fun authenticatedUserId(authentication: Authentication): Long =
        authentication.name.toLongOrNull() ?: throw IllegalStateException("Invalid authenticated user")

    @GetMapping
    fun profile(authentication: Authentication): CurrentUserResponse =
        profileService.getProfile(authenticatedUserId(authentication))

    @PatchMapping
    fun update(
        authentication: Authentication,
        @Valid @RequestBody request: ProfileUpdateRequest
    ): CurrentUserResponse = profileService.updateProfile(authenticatedUserId(authentication), request)

    @PutMapping("/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadImage(
        authentication: Authentication,
        @RequestPart("image") image: org.springframework.web.multipart.MultipartFile
    ): CurrentUserResponse = profileService.uploadImage(authenticatedUserId(authentication), image)

    @DeleteMapping("/image")
    fun deleteImage(authentication: Authentication): CurrentUserResponse =
        profileService.deleteImage(authenticatedUserId(authentication))

    @GetMapping("/image")
    fun image(authentication: Authentication): ResponseEntity<ByteArray> {
        val stored = profileService.image(authenticatedUserId(authentication))
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(stored.contentType))
            .contentLength(stored.bytes.size.toLong())
            .cacheControl(CacheControl.noCache().cachePrivate())
            .body(stored.bytes)
    }
}


@RestController
@RequestMapping("/api/v1/admin/commission-roles")
class CommissionRoleAdminController(
    private val commissionRateService: com.recharge.backend.service.CommissionRateService,
    private val users: com.recharge.backend.repository.UserRepository,
    private val roleAccess: com.recharge.backend.service.RoleAccessService
) {
    private fun currentUser(authentication: Authentication) = authentication.name.toLongOrNull()?.let { users.findById(it).orElseThrow { IllegalArgumentException("User not found") } }
        ?: throw IllegalStateException("Invalid authenticated user")
    private fun requireAdminPermission(authentication: Authentication) = roleAccess.requirePermission(currentUser(authentication), "MANAGE_COMMISSION_RATES")

    @GetMapping
    fun list(authentication: Authentication): List<RoleCommissionRateResponse> {
        requireAdminPermission(authentication)
        return commissionRateService.allRates().map { RoleCommissionRateResponse(it.role, it.commissionPercent.setScale(2), it.active) }
    }

    @PutMapping("/{role}")
    fun update(authentication: Authentication, @PathVariable role: String, @Valid @RequestBody request: UpdateRoleCommissionRateRequest): RoleCommissionRateResponse {
        requireAdminPermission(authentication)
        val saved = commissionRateService.upsert(role, request.commissionPercent, request.active)
        return RoleCommissionRateResponse(saved.role, saved.commissionPercent.setScale(2), saved.active)
    }
}

@RestController
@RequestMapping("/api/v1/services")
class ServicesController(
    private val vendors: com.recharge.backend.repository.AdminVendorRepository
) {
    @GetMapping("/vendors")
    fun activeVendors(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) city: String?
    ): List<AdminVendorResponse> {
        val normalizedCategory = category?.trim()?.uppercase()
        val normalizedCity = city?.trim()?.lowercase()
        return vendors.findAllByActiveTrueOrderByCreatedAtDesc()
            .asSequence()
            .filter { normalizedCategory.isNullOrBlank() || it.category.equals(normalizedCategory, true) }
            .filter { normalizedCity.isNullOrBlank() || it.city.lowercase().contains(normalizedCity) }
            .map { AdminVendorResponse(
                id = requireNotNull(it.id).toString(), name = it.name, category = it.category,
                city = it.city, phone = it.phone, commissionRate = it.commissionRate.setScale(2),
                active = it.active, createdAt = it.createdAt
            ) }
            .toList()
    }
}

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val adminService: com.recharge.backend.service.AdminService,
    private val users: com.recharge.backend.repository.UserRepository,
    private val roleAccess: com.recharge.backend.service.RoleAccessService
) {
    private fun currentUser(authentication: Authentication) = authentication.name.toLongOrNull()?.let { users.findById(it).orElseThrow { IllegalArgumentException("User not found") } }
        ?: throw IllegalStateException("Invalid authenticated user")

    @GetMapping("/dashboard")
    fun dashboard(authentication: Authentication): AdminDashboardResponse =
        adminService.dashboard(currentUser(authentication))

    @GetMapping("/visible-roles")
    fun visibleRoles(authentication: Authentication): PortalRolesResponse {
        val viewer = currentUser(authentication)
        roleAccess.requirePermission(viewer, "VIEW_USERS")
        return PortalRolesResponse(roleAccess.visibleRolesFor(viewer.role).toList())
    }

    @GetMapping("/users")
    fun users(
        authentication: Authentication,
        @RequestParam(required = false, defaultValue = "ALL") role: String,
        @RequestParam(required = false, defaultValue = "today-high") sort: String
    ): List<AdminUserSummaryResponse> =
        adminService.users(role, sort, currentUser(authentication))

    @GetMapping("/users/{publicId}")
    fun userDetail(authentication: Authentication, @PathVariable publicId: String): AdminUserDetailResponse =
        adminService.userDetail(currentUser(authentication), publicId)

    @GetMapping("/users/{publicId}/recharges")
    fun userRecharges(
        authentication: Authentication,
        @PathVariable publicId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): RechargeHistoryResponse =
        adminService.rechargeHistory(currentUser(authentication), publicId, page, size)

    @GetMapping("/users/{publicId}/wallet-history")
    fun userWalletHistory(
        authentication: Authentication,
        @PathVariable publicId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): WalletHistoryResponse =
        adminService.walletHistory(currentUser(authentication), publicId, page, size)

    @GetMapping("/users/{publicId}/profile-image")
    fun userProfileImage(
        authentication: Authentication,
        @PathVariable publicId: String
    ): ResponseEntity<ByteArray> {
        val stored = adminService.profileImage(currentUser(authentication), publicId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(stored.contentType))
            .contentLength(stored.bytes.size.toLong())
            .cacheControl(CacheControl.noCache().cachePrivate())
            .body(stored.bytes)
    }

    @GetMapping("/vendors")
    fun vendors(authentication: Authentication): List<AdminVendorResponse> =
        adminService.vendorList(currentUser(authentication))

    @PostMapping("/vendors")
    fun createVendor(authentication: Authentication, @Valid @RequestBody request: CreateAdminVendorRequest): AdminVendorResponse =
        adminService.createVendor(currentUser(authentication), request)
}
